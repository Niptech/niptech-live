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


object Robot {

  def apply(chatRoom: ActorRef) {

    // Create an Iteratee that logs all messages to the console.
    val loggerIteratee = Iteratee.foreach[JsValue](event => Logger("robot").info(event.toString))

    implicit val timeout = Timeout(1 second)
    // Make the robot join the room
    chatRoom ? (Join("Syde Bot", "syde")) map {
      case Connected(robotChannel) =>
        // Apply this Enumerator on the logger.
        robotChannel |>> loggerIteratee
    }

    // Make the robot talk every 30 seconds
    Akka.system.scheduler.schedule(
      3 minutes,
      3 minutes,
      chatRoom,
      SayQuote("Syde Bot"))
  }

}

object ChatRoom {

  implicit val timeout = Timeout(1 second)

  def initialize = {
    val roomActor = Akka.system.actorOf(Props[ChatRoom], "chatroom")

    Logger.info("roomactor path :" + roomActor.path.toString)

    // Create a bot user (just for fun)
    Robot(roomActor)

    initTwitterListener(roomActor)

    roomActor
  }

  def default = Akka.system.actorFor("/user/chatroom")

  def initTwitterListener(chatRoom: ActorRef) = {

    chatRoom ? (Join("Twitter", "niptechlive"))

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
    Await.result(f, 10 seconds)
  }

  def join(username: String, twitterId: String): scala.concurrent.Future[(Iteratee[JsValue, _], Enumerator[JsValue])] = {

    (default ? Join(username, twitterId)).map {

      case Connected(enumerator) =>

        // Create an Iteratee to consume the feed
        val iteratee = Iteratee.foreach[JsValue] {
          event =>
            default ! Talk(username, (event \ "text").as[String])
        }.mapDone {
          _ =>
            default ! Quit(username)
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
    "The future belongs to those who believe in the beauty of their dreams. Eleanor Roosevelt")

  var members = Set.empty[String]
  var twitterIds = Map.empty[String, String] withDefaultValue ("http://www.gravatar.com/avatar/none?s=20")
  val (chatEnumerator, chatChannel) = Concurrent.broadcast[JsValue]

  def receive = {

    case Join(username, twitterId) => {
      if (members.contains(username)) {
        sender ! CannotConnect("Ce pseudo est déjà utilisé")
      } else {
        members = members + username
        twitterIds += username -> TwitterClient.getUserImageUrl(twitterId)
        sender ! Connected(chatEnumerator)
        self ! NotifyJoin(username)
      }
    }

    case NotifyJoin(username) => {
      notifyAll("join", username, "vient d'entrer dans la ChatRoom")
    }

    case Talk(username, text) => {
      notifyAll("talk", username, text)
      if (Cache.getOrElse[Boolean]("twitterBroadcast") {false})
        try {TwitterClient.twitter.updateStatus((username + " - " + text).take(140))}
          catch {case exc => Logger.error(exc.getMessage)}
    }

    case Tweet(status) => {
      notifyAll("talk", "Twitter", "@" + status.getUser().getScreenName() + " - " + status.getText())
    }

    case SayQuote(username) => {
      val quote = quotes(new Random(new java.util.Date().getTime()).nextInt(quotes.size - 1))
      notifyAll("talk", username, quote)
    }

    case Quit(username) => {
      members = members - username
      twitterIds -= username
      notifyAll("quit", username, "a quitté la ChatRoom")
    }

    case NbUsers() => sender ! members.size

  }

  def notifyAll(kind: String, user: String, text: String) {
    val msg = JsObject(
      Seq(
        "kind" -> JsString(kind),
        "user" -> JsString(user),
        "avatar" -> JsString(twitterIds(user)),
        "message" -> JsString(text),
        "members" -> JsArray(members.toList.map(JsString))))
    chatChannel.push(msg)
  }
}

case class Join(username: String, twitterId: String)

case class Quit(username: String)

case class Talk(username: String, text: String)

case class Tweet(status: Status)

case class NotifyJoin(username: String)

case class SayQuote(username: String)

case class NbUsers()

case class Connected(enumerator: Enumerator[JsValue])

case class CannotConnect(msg: String)


