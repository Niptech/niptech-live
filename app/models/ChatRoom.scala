package models

import akka.actor._
import scala.concurrent.duration._
import scala.concurrent._
import scala.util.Random
import play.api._

import cache.Cache
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._

import akka.util.Timeout
import akka.pattern.ask

import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import twitter4j._

import scala.collection


object ChatRoom {

  implicit val timeout = Timeout(5 second)

  val members = collection.mutable.ArrayBuffer[String]()

  def initialize = {

    robot

    initTwitterListener

    Logger.info("ChatRoom initialized")


  }

  def join(username: String) = {
    members += username
    val memberActor = Akka.system.actorOf(Props(new Member(username, username, "")), username)
    val r = Akka.system.eventStream.subscribe(memberActor, classOf[ChatMessage])
    Logger.debug("Soucrit : " + r.toString)
    Logger.debug(username + " vient de rejoindre la chatroom")
    memberActor
  }

  def robot = Akka.system.scheduler.schedule(
    5 minutes,
    5 minutes,
    ChatRoom.join("Syde_Bot"),
    SayQuote())


  def initTwitterListener = {

    val twitterActor = join("Twitter")

    val twitterStream = TwitterClient.twitterStream

    val listener = new StatusListener() {
      @Override
      def onStatus(st: Status) = twitterActor ? Tweet(st)

      @Override
      def onDeletionNotice(st: StatusDeletionNotice) = {
        System.out.println("Got a status deletion notice id:" + st.getStatusId())
      }

      @Override
      def onTrackLimitationNotice(numberOfLimitedStatuses: Int) = {
        System.out.println("Got track limitation notice:" + numberOfLimitedStatuses)
      }

      @Override
      def onScrubGeo(userId: Long, upToStatusId: Long) = {
        System.out.println("Got scrub_geo event userId:" + userId + " upToStatusId:" + upToStatusId)
      }

      @Override
      def onStallWarning(warning: StallWarning) {
        System.out.println("Got stall warning:" + warning);
      }

      @Override
      def onException(ex: Exception) {
        ex.printStackTrace()
      }
    }
    twitterStream.addListener(listener)
    twitterStream.filter(new FilterQuery(0, Array[Long](), Array[String]("niptechlive")));
  }

  def nbUsers: Int = members.size

  def username(userid: String): String = {
    val member = Akka.system.actorFor("/user/" + userid)
    val f = (member ? GetUsername()).map {
      case name: String => name
      case _ => ""
    }
    Await.result(f, 10 second)
  }

  def changeName(userid: String, newName: String, imageUrl: Option[String]) = {
    val member = Akka.system.actorFor("/user/" + userid)
    member ! ChangeName(newName, imageUrl)
  }


  def quit(userid: String) = {
    members -= username(userid)
    Cache.remove(userid)
    val memberActor = Akka.system.actorFor("/user/" + userid)
    Akka.system.eventStream.unsubscribe(memberActor, classOf[ChatMessage])
    Akka.system.stop(memberActor)
  }

}

class Member(var userid: String, var username: String, var imageUrl: String) extends Actor {

  val quotes = List(
    "A great person attracts great people and knows how to hold them together. Johann Wolfgang Von Goethe",
    "A man is but the product of his thoughts what he thinks, he becomes. Ghandi",
    "Motivation is the art of getting people to do what you want them to do because they want to do it. Eisenhower",
    "Hire character. Train skill. Peter Schutz",
    "The more you lose yourself in something bigger than yourself, the more energy you will have. Norman Peale",
    "Some men see things as they are and say, ‘Why’? I dream of things that never were and say, ‘Why not’? Robert Kennedy",
    "What the mind can conceive, the mind can achieve. Napoleon Hill",
    "Do not expect something for nothing. Be willing to give an equivalent value for all that you desire. Napoleon Hill",
    "It’s better to be king of your world, rather than a peasant in another man’s land. Satya Hanif",
    "The future belongs to those who believe in the beauty of their dreams. Eleanor Roosevelt",
    "Yesterday’s home runs don’t win today’s games. Babe Ruth",
    "Logic will get you from A to B. Imagination will take you everywhere. Albert Einstein",
    "It’s not that I’m so smart, it’s just that I stay with the problems longer. Albert Einstein",
    "Every sale has five basic obstacles: no need, no money, no hurry, no desire, no trust. Zig Ziglar",
    "All good leadership skills can be boiled down to 2 categories: Capacity to Connect & Capacity to Initiate Skillful Change. Bill_Gross",
    "A stumbling block to the pessimist is a stepping stone to the optimist.",
    "When you do the common things in life in an uncommon way, you will command the attention of the world. George Washington Carver",
    "Excuses are the nails used to build a house of failure. Dan Wilder",
    "If you want to fly, you have to give up the things that weigh you down. Unknown",
    "The primary cause of unhappiness is never the situation but your thoughts about it. Eckhart Tolle",
    "Failure + failure + failure = success. You only fail when you quit. Jack Hyles",
    "Don’t measure yourself by what you have accomplished, but by what you should have accomplished with your ability. John Wooden",
    "Luck is what happens when preparation meets opportunity. Unknown",
    "Choose a job you love and you will never have to work a day in your life. Confucius",
    "You were born with wings. Why prefer to crawl through life? Jalaluddin Rumi")

  val (chatEnumerator, chatChannel) = Concurrent.broadcast[JsValue]

  def receive = {

    case Connect() => sender ! Connected(chatEnumerator)

    case NotifyJoin() => {
      notifyAll("join", "vient d'entrer dans la ChatRoom")
    }

    case Talk(text) => {
      text match {
        case save if save startsWith ("save:") =>
          Cache.getAs[Twitter](username).foreach(t => t.sendDirectMessage(username, text.takeRight(text.size - 5).take(140)))
        case changeuser if changeuser startsWith ("nickname:") =>
          self ! ChangeName(text.takeRight(text.size - 9), None)

        case _ =>
          notifyAll("talk", text)
          if (Cache.getOrElse[Boolean]("twitterBroadcast")(false))
            try {
              TwitterClient.twitter.updateStatus((username.takeRight(username.size - 1) + " - " + text).take(140))
            }
            catch {
              case exc: Throwable => Logger.error(exc.getMessage)
            }
      }
    }

    case Tweet(status) => {
      notifyAll("talk", "@" + status.getUser().getScreenName() + " - " + status.getText())
    }

    case SayQuote() => {
      val quote = quotes(new Random(new java.util.Date().getTime()).nextInt(quotes.size - 1))
      notifyAll("talk", quote)
    }

    case GetUsername() => sender ! username

    case ChangeName(newname, image) =>
      ChatRoom.members -= username
      ChatRoom.members += newname
      notifyAll("talk", username + " a changé son nom en " + newname)
      username = newname
      image.map(url => imageUrl = url)

    case ChatMessage(msg: JsObject) =>
      Logger.debug(username + " - Reçu : " + msg.toString)
      chatChannel.push(msg)

  }

  def notifyAll(kind: String, text: String) {
    val msg = JsObject(
      Seq(
        "kind" -> JsString(kind),
        "user" -> JsString(username),
        "avatar" -> JsString(imageUrl),
        "message" -> JsString(text),
        "members" -> JsArray(ChatRoom.members.map(JsString))))
    Logger.debug("Notify all " + username + "-" + text)
    Akka.system.eventStream.publish(ChatMessage(msg))
  }
}

case class Connect()

case class Talk(text: String)

case class ChatMessage(text: JsObject)

case class Tweet(status: Status)

case class NotifyJoin()

case class SayQuote()

case class GetUsername()

case class ChangeName(newName: String, imageUrl: Option[String])

case class Connected(enumerator: Enumerator[JsValue])

case class CannotConnect(msg: String)


