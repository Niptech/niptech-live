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
import collection.parallel.mutable


object Robot {

  def apply(chatRoom: ActorRef) {

    // Create an Iteratee that logs all messages to the console.
    val loggerIteratee = Iteratee.foreach[JsValue](event => Logger("robot").info(event.toString))

    implicit val timeout = Timeout(1 second)
    // Make the robot join the room
    chatRoom ? (Join("Syde Bot")) map {
      case Connected(robotChannel) =>
        // Apply this Enumerator on the logger.
        robotChannel |>> loggerIteratee
    }

    // Make the robot talk every 30 seconds
    Akka.system.scheduler.schedule(
      5 minutes,
      5 minutes,
      chatRoom,
      SayQuote("Syde Bot"))
  }

}

object ChatRoom {

  implicit val timeout = Timeout(5 second)

  def initialize = {
    val roomActor = Akka.system.actorOf(Props[ChatRoom], "chatroom")

    // Create a bot user (just for fun)
    Robot(roomActor)

    initTwitterListener(roomActor)

    Logger.info("ChatRoom initialized")

    roomActor
  }

  def clean = {
    Akka.system.stop(default)
    Logger.info("ChatRoom shutdown")
  }

  def default = Akka.system.actorFor("/user/chatroom")

  def initTwitterListener(chatRoom: ActorRef) = {

    chatRoom ? (Join("Twitter"))

    val twitterStream = TwitterClient.twitterStream

    val listener = new StatusListener() {
      @Override
      def onStatus(st: Status) = chatRoom ? Tweet(st)

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

  def nbUsers: Int = {
    val f = (default ? NbUsers()).map {
      case n: Int => n
      case _ => 0
    }
    Await.result(f, 10 second)
  }

  def username(userid: String): String = {
    val f = (default ? GetUsername(userid)).map {
      case name: String => name
      case _ => ""
    }
    Await.result(f, 10 second)
  }

  def changeName(userid: String, newName: String, imageUrl: Option[String]) = default ! ChangeName(userid, newName, imageUrl)


  def quit(userid: String) = default ! Quit(userid)

  def join(userid: String): scala.concurrent.Future[(Iteratee[JsValue, _], Enumerator[JsValue])] = {

    (default ? Join(userid)).map {

      case Connected(enumerator) =>

        // Create an Iteratee to consume the feed
        val iteratee = Iteratee.foreach[JsValue] {
          event =>
            default ! Talk(userid, (event \ "text").as[String])
        }.mapDone {
          _ =>
           // default ! Quit(userid)
        }

        (iteratee, enumerator)

      case CannotConnect(error) =>

        // Connection error

        // A finished Iteratee sending EOF
        val iteratee = Done[JsValue, Unit]((), Input.EOF)

        // Send an error and close the socket
        val enumerator = Enumerator[JsValue](JsObject(Seq("error" -> JsString(error)))).andThen(Enumerator.enumInput(Input.EOF))

        (iteratee, enumerator)

    }

  }

}

class ChatRoom extends Actor {

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

  val members = scala.collection.mutable.Map.empty[String, Member]
  val (chatEnumerator, chatChannel) = Concurrent.broadcast[JsValue]

  def receive = {

    case Join(userid) => {
      members.get(userid).map {
        member =>
          sender ! Connected(chatEnumerator)
      } getOrElse {
        members.put(userid, Member(userid, "http://www.gravatar.com/avatar/none?s=20"))
        sender ! Connected(chatEnumerator)
        // self ! NotifyJoin(username)
      }
    }

    case NotifyJoin(userid) => {
      notifyAll("join", userid, "vient d'entrer dans la ChatRoom")
    }

    case Talk(userid, text) => {
      val username = members(userid).username
      text match {
        case save if save startsWith ("save:") =>
          Cache.getAs[Twitter](username).foreach(t => t.sendDirectMessage(username, text.takeRight(text.size - 5).take(140)))
        case changeuser if changeuser startsWith ("nickname:") =>
          val newname = text.takeRight(text.size - 9)
          members(userid).username = newname
          notifyAll("talk", userid, username + " a changé son nom en " + newname)
        case _ =>
          notifyAll("talk", userid, text)
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
      notifyAll("talk", "Twitter", "@" + status.getUser().getScreenName() + " - " + status.getText())
    }

    case SayQuote(userid) => {
      val quote = quotes(new Random(new java.util.Date().getTime()).nextInt(quotes.size - 1))
      notifyAll("talk", userid, quote)
    }

    case Quit(username) => {
      members.remove(username)
      Cache.remove(username)
      // notifyAll("quit", username, "a quitté la ChatRoom")
    }

    case NbUsers() => sender ! members.size

    case GetUsername(userid) =>
      val name = members.get(userid) map (member => member.username) getOrElse (userid)
      sender ! name

    case ChangeName(userid, newname, image) =>
      members(userid).username = newname
      image.map(url=>members(userid).imageUrl = url)

  }

  def notifyAll(kind: String, userid: String, text: String) {
    val member = members(userid)
    val msg = JsObject(
      Seq(
        "kind" -> JsString(kind),
        "user" -> JsString(member.username),
        "avatar" -> JsString(member.imageUrl),
        "message" -> JsString(text),
        "members" -> JsArray(members.map(p => p._2.username).toList.map(JsString))))
    chatChannel.push(msg)
  }
}

case class Join(userid: String)

case class Quit(username: String)

case class Talk(username: String, text: String)

case class Tweet(status: Status)

case class NotifyJoin(username: String)

case class SayQuote(username: String)

case class NbUsers()

case class GetUsername(userid: String)

case class ChangeName(userid: String, newName: String, imageUrl: Option[String])

case class Connected(enumerator: Enumerator[JsValue])

case class CannotConnect(msg: String)

case class Member(var username: String, var imageUrl: String)

