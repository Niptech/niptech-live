package models

import akka.actor._
import scala.concurrent.duration._
import scala.concurrent._
import play.api._

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
    Akka.system.scheduler.schedule(
      30 seconds,
      30 seconds,
      memberActor,
      CheckTimeout())
    Logger.info(username + " vient de rejoindre la chatroom")
    memberActor
  }

  def robot = {
    val robotName = "Syde_Bot"
    members += robotName
    val robotActor = Akka.system.actorOf(Props(new Robot(robotName)), robotName)
    Akka.system.scheduler.schedule(
      5 minutes,
      5 minutes,
      robotActor,
      SayQuote())
  }


  def initTwitterListener = {

    val twitterActor = Akka.system.actorOf(Props[TwitterMember], "Twitter")

    members += "Twitter"

    val twitterStream = TwitterClient.twitterStream

    val listener = new StatusListener() {
      @Override
      def onStatus(st: Status) = twitterActor ! Tweet(st)

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


  def quit(userid: String) = Akka.system.actorFor("/user/" + userid) ! Quit()

}

