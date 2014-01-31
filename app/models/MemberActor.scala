package models

import scala.concurrent.duration._

import scala.util.{Try, Random}
import play.api._

import cache.Cache

import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._

import scala.concurrent._
import ExecutionContext.Implicits.global

import play.api.Play._
import play.api.libs.concurrent.Execution.Implicits._
import twitter4j._
import com.typesafe.config.ConfigFactory
import play.api.libs.json.JsArray
import play.api.libs.json.JsString
import play.api.libs.json.JsObject

import akka.actor._
import akka.util.Timeout
import akka.pattern.ask


class Member(var userid: String, var username: String, var imageUrl: String, var ipAddr: String) extends Actor {

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
        notifyAll("talk", "")
      }
    }

    case Disconnect() => {
      Logger.debug(username + " disconnected")
      isConnected = false
      //notifyAll("talk", username + " s'est déconnecté de la ChatRoom")
      // Empty message to update the list of users
      notifyAll("talk", "")
    }

    case NotifyJoin() => {
      notifyAll("join", "vient d'entrer dans la ChatRoom")
    }

    case Talk(text) => {
      connectedDeadline = connectedTimeout.fromNow
      disconnectedDeadline = disconnectedTimeout.fromNow
      val bannedIPs = Cache.getOrElse[List[String]]("bannedIPs")(List())
      if (!bannedIPs.contains(ipAddr)) {
        text match {
          case save if text startsWith ("save:") =>
            Cache.getAs[Twitter](username).foreach(t => t.sendDirectMessage(username, text.takeRight(text.size - 5).take(140)))
          case tirage if text startsWith ("tirage ") =>
            val parms = tirage.split(" ")
            val nbGagnants = Try(parms(1).toInt)
            val preuve = parms(2)
            nbGagnants.map(n => self ! Tirage(n, preuve))
          case share if text startsWith ("shareOnTwitter:") =>
            Logger.debug("Sharing on twitter: " + share.takeRight(share.size - 15))
            Cache.getAs[Twitter](username).foreach(t => t.updateStatus(share.takeRight(share.size - 15).take(140)))
          case changeuser if text startsWith ("nickname:") =>
            self ! ChangeName(text.takeRight(text.size - 9), None)
          case ban if text startsWith ("/ban ") =>
            val moderator = Cache.getOrElse[String]("moderator")("")
            if (username == moderator) {
              val bannedName = text drop 5
              Logger.info(bannedName + "est banni de la ChatRoom")
              Akka.system.eventStream.publish(BanFromChatroom(bannedName))
            }

          case _ =>
            notifyAll("talk", text)
            if (Cache.getOrElse[Boolean]("twitterBroadcast")(false) && TwitterClient.isValid)
              try {
                TwitterClient.twitter.updateStatus((username + " - " + text).take(140))
              }
              catch {
                case exc: Throwable => Logger.error(exc.getMessage)
              }
        }
      }
      else
        sendToSelfOnly("Vous êtes banni de la ChatRoom. Impossible de parler")
    }

    case GetUsername() => sender ! username

    case ChangeName(newname, image) =>
      val escapedNewname = xml.Utility.escape(newname)
      ChatRoom.members -= username
      ChatRoom.members += escapedNewname
      sendToSelfOnly("Vous avez changé votre nom en " + escapedNewname)
      // Empty message to update the list of users
      notifyAll("talk", "")
      username = escapedNewname
      image.map(url => imageUrl = url)

    case ChatMessage(msg: JsObject) =>
      Logger.debug(username + " - Reçu : " + msg.toString)
      chatChannel.push(msg)

    case FileSent(name: String) =>
      notifyAll("file", name)

    case Tirage(nbGagnants: Int, preuve: String) =>
      val winners = Random.shuffle(ChatRoom.membersActorsId.filter(id => id != userid)).take(nbGagnants)
      val liste = winners.map {
        winnerId =>
          val won = JsObject(
            Seq(
              "kind" -> JsString("talk"),
              "user" -> JsString("GAGNANT"),
              "avatar" -> JsString(""),
              "message" -> JsString(preuve),
              "members" -> JsArray(ChatRoom.members.map(JsString))))
          val member = Akka.system.actorFor("/user/" + winnerId)
          member ! ChatMessage(won)
          ChatRoom.username(winnerId)
      }
      notifyAll("talk", "Les gagnants sont " + liste.mkString(", "))


    case CheckTimeout() =>
      val mustQuit = (!isConnected && disconnectedDeadline.isOverdue()) || (isConnected && connectedDeadline.isOverdue())
      Logger.debug("Timeout for" + username + " value :" + mustQuit)
      if (mustQuit) {
        Logger.info("Timeout for " + username + ". Leaving chatroom")
        self ! Quit()
      }

    case Quit() =>
      ChatRoom.members -= username
      ChatRoom.membersActorsId -= userid
      Cache.remove(userid)
      //notifyAll("talk", username + " a quitté la ChatRoom")
      // Empty message to update the list of users
      notifyAll("talk", "")
      Akka.system.eventStream.unsubscribe(self, classOf[BanFromChatroom])
      Akka.system.eventStream.unsubscribe(self, classOf[ChatMessage])
      Akka.system.stop(self)

    case BanFromChatroom(bannedName: String) =>
      if (bannedName == username) {
        val bannedIPs = Cache.getOrElse[List[String]]("bannedIPs")(List())
        Cache.set("bannedIPs", ipAddr :: bannedIPs)
        sendToSelfOnly("//BANNI//")
      }

  }

  def notifyAll(kind: String, text: String) {
    val msg = JsObject(
      Seq(
        "kind" -> JsString(kind),
        "user" -> JsString(username),
        "avatar" -> JsString(imageUrl),
        "message" -> JsString(addHyperlink(xml.Utility.escape(text))),
        "members" -> JsArray(ChatRoom.members.map(JsString))))
    Akka.system.eventStream.publish(ChatMessage(msg))
  }

  def sendToSelfOnly(message: String) {
    val msg = JsObject(
      Seq(
        "kind" -> JsString("talk"),
        "user" -> JsString(username),
        "avatar" -> JsString(imageUrl),
        "message" -> JsString(message),
        "members" -> JsArray(ChatRoom.members.map(JsString))))
    self ! ChatMessage(msg)
  }

}

class Robot(username: String) extends Member(username, username, "", "127.0.0.1") {

  def quotes = ConfigFactory.load("niptechquotes").getStringList("quotes")


  override def receive = {
    case SayQuote() => {
      val quote = quotes.get(new Random(new java.util.Date().getTime()).nextInt(quotes.size - 1))
      notifyAll("talk", quote)
    }
  }

}

class TwitterMember extends Member("Twitter", "Twitter", "", "127.0.0.1") {
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

case class FileSent(name: String)

case class Tirage(nbGagnants: Int, preuve: String)

case class BanFromChatroom(username: String)


