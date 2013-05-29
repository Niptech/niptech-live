package models

import akka.actor._
import scala.concurrent.duration._
import scala.util.Random
import play.api._

import cache.Cache

import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._

import play.api.Play._
import play.api.libs.concurrent.Execution.Implicits._
import twitter4j._
import com.typesafe.config.ConfigFactory


class Member(var userid: String, var username: String, var imageUrl: String) extends Actor {

  val connectedTimeout = 90 minutes
  val disconnectedTimeout = 90 seconds

  var connectedDeadline = connectedTimeout fromNow
  var disconnectedDeadline = disconnectedTimeout fromNow

  var isConnected = false

  val (chatEnumerator, chatChannel) = Concurrent.broadcast[JsValue]

  def addHyperlink(text: String): String =
    text match {
      case htext if text.contains("http://") =>
        val hstart = htext.indexOf("http://")
        val hend = if (htext.indexOf(' ', hstart) == -1) htext.size else htext.indexOf(' ', hstart)
        val url = htext.substring(hstart, hend)
        htext.replace(url, "<a href='" + url + "' target=_blank>" + url + "</a>")
      case httpsurl if text.contains("https://") =>
        val hstart = httpsurl.indexOf("https://")
        val hend = if (httpsurl.indexOf(' ', hstart) == -1) httpsurl.size else httpsurl.indexOf(' ', hstart)
        val url = httpsurl.substring(hstart, hend)
        httpsurl.replace(url, "<a href='" + url + "' target=_blank>" + url + "</a>")
      case _ => text
    }

  def receive = {

    case Connect() => {
      isConnected = true
      val msg = JsObject(
        Seq(
          "kind" -> JsString("talk"),
          "user" -> JsString(username),
          "avatar" -> JsString(imageUrl),
          "message" -> JsString("Vous êtes connecté à la ChatRoom"),
          "members" -> JsArray(ChatRoom.members.map(JsString))))
      sender ! Connected(chatEnumerator)
      Akka.system.scheduler.scheduleOnce(1 second) {
        self ! ChatMessage(msg)
      }
    }

    case Disconnect() => {
      Logger.debug(username + " disconnected")
      isConnected = false
    }

    case NotifyJoin() => {
      notifyAll("join", "vient d'entrer dans la ChatRoom")
    }

    case Talk(text) => {
      connectedDeadline = connectedTimeout.fromNow
      disconnectedDeadline = disconnectedTimeout.fromNow
      text match {
        case save if text startsWith ("save:") =>
          Cache.getAs[Twitter](username).foreach(t => t.sendDirectMessage(username, text.takeRight(text.size - 5).take(140)))
        case share if text startsWith ("shareOnTwitter:") =>
          Logger.debug("Sharing on twitter: " + share.takeRight(share.size - 15))
          Cache.getAs[Twitter](username).foreach(t => t.updateStatus(share.takeRight(share.size - 15).take(140)))
        case changeuser if text startsWith ("nickname:") =>
          self ! ChangeName(text.takeRight(text.size - 9), None)

        case _ =>
          notifyAll("talk", addHyperlink(text))
          if (Cache.getOrElse[Boolean]("twitterBroadcast")(false))
            try {
              TwitterClient.twitter.updateStatus((username + " - " + text).take(140))
            }
            catch {
              case exc: Throwable => Logger.error(exc.getMessage)
            }
      }
    }

    case GetUsername() => sender ! username

    case ChangeName(newname, image) =>
      ChatRoom.members -= username
      ChatRoom.members += newname
      notifyAll("talk", username + " a changé son nom en " + newname)
      Logger.info(username + " a changé son nom en " + newname)
      username = newname
      image.map(url => imageUrl = url)

    case ChatMessage(msg: JsObject) =>
      Logger.debug(username + " - Reçu : " + msg.toString)
      chatChannel.push(msg)

    case CheckTimeout() =>
      val mustQuit = (!isConnected && disconnectedDeadline.isOverdue()) || (isConnected && connectedDeadline.isOverdue())
      Logger.debug("Timeout for" + username + " value :" + mustQuit)
      if (mustQuit) {
        Logger.info("Timeout for " + username + ". Leaving chatroom")
        self ! Quit()
      }

    case Quit() =>
      Logger.debug(username + " quitte la chatroom")
      ChatRoom.members -= username
      Cache.remove(userid)
      Akka.system.eventStream.unsubscribe(self, classOf[ChatMessage])
      Akka.system.stop(self)

  }

  def notifyAll(kind: String, text: String) {
    val msg = JsObject(
      Seq(
        "kind" -> JsString(kind),
        "user" -> JsString(username),
        "avatar" -> JsString(imageUrl),
        "message" -> JsString(text),
        "members" -> JsArray(ChatRoom.members.map(JsString))))
    Akka.system.eventStream.publish(ChatMessage(msg))
  }
}

class Robot(username: String) extends Member(username, username, "") {

  def quotes = ConfigFactory.load("niptechquotes").getStringList("quotes")


  override def receive = {
    case SayQuote() => {
      val quote = quotes.get(new Random(new java.util.Date().getTime()).nextInt(quotes.size - 1))
      notifyAll("talk", quote)
    }
  }

}

class TwitterMember extends Member("Twitter", "Twitter", "") {
  override def receive = {
    case Tweet(status) => {
      notifyAll("talk", "@" + status.getUser().getScreenName() + " - " + status.getText())
    }
  }
}

case class Connect()

case class Disconnect()

case class Quit()

case class Talk(text: String)

case class ChatMessage(text: JsObject)

case class Tweet(status: Status)

case class NotifyJoin()

case class SayQuote()

case class GetUsername()

case class CheckTimeout()

case class ChangeName(newName: String, imageUrl: Option[String])

case class Connected(enumerator: Enumerator[JsValue])

case class CannotConnect(msg: String)


