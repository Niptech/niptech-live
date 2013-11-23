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
import scala.collection.JavaConversions._

import scala.collection
import com.typesafe.config.ConfigFactory
import play.libs.Json._
import java.io.FileWriter


object ChatRoom {

  implicit val timeout = Timeout(5 second)

  val members = collection.mutable.ArrayBuffer[String]()

  def initialize = {

    // robot


    if (TwitterClient.isValid) {

      initTwitterListener
      Logger.info("twitter listener initialized")

    }

    Logger.info("ChatRoom initialized")

    val writerActor = Akka.system.actorOf(Props(new FileWriterActor()), "FileWriter")
    Akka.system.eventStream.subscribe(writerActor, classOf[ChatMessage])

  }

  def join(userid: String) = {
    var username = newUserid
    members += username
    Logger.info(members.size.toString + " membre connectés")
    val memberActor = Akka.system.actorOf(Props(new Member(userid, username, "")), userid)
    val r = Akka.system.eventStream.subscribe(memberActor, classOf[ChatMessage])
    Akka.system.scheduler.schedule(
      30 seconds,
      30 seconds,
      memberActor,
      CheckTimeout())
    Logger.info(username + " vient de rejoindre la chatroom")
    memberActor
  }


  def newUserid: String = {
    var n = 1
    while (members.contains("Guest" + n.toString))
      n += 1
    "Guest" + n.toString
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
      def onStatus(st: Status) = {
        twitterActor ! Tweet(st)
  /*      val text = st.getText
        if (text.contains("#quote")) {
          Logger.info("NEW QUOTE : " + st.getText)
          val quotes = ConfigFactory.load("niptechquotes").getStringList("quotes")
          quotes.add(st.getText.replaceAll("\"", ""))
          val writer = new FileWriter("./niptech-live/niptechquotes.conf")
          writer.write("quotes = [")
          val content = quotes.toList.map(quote => "\"" + quote + "\"").mkString(",\r\n") + "]"
          Logger.info(content)
          writer.write(content)
          writer.close()
        }                    */
      }

      @Override
      def onDeletionNotice(st: StatusDeletionNotice) = {
        Logger.info("Got a status deletion notice id:" + st.getStatusId())
      }

      @Override
      def onTrackLimitationNotice(numberOfLimitedStatuses: Int) = {
        Logger.info("Got track limitation notice:" + numberOfLimitedStatuses)
      }

      @Override
      def onScrubGeo(userId: Long, upToStatusId: Long) = {
        Logger.info("Got scrub_geo event userId:" + userId + " upToStatusId:" + upToStatusId)
      }

      @Override
      def onStallWarning(warning: StallWarning) {
        Logger.info("Got stall warning:" + warning);
      }

      @Override
      def onException(exc: Exception) {
        Logger.error("Exception on Twitter stream", exc)
      }
    }
    twitterStream.addListener(listener)
    twitterStream.filter(new FilterQuery(0, Array[Long](), Array[String]("willandco", "#waclive")))
  }

  def nbUsers: Int = members.size

  def username(userid: String): String = {
    val member = Akka.system.actorFor("/user/" + userid)
    if (member.isTerminated)
      "None"
    else {
      val f = (member ? GetUsername()).map {
        case name: String => name
        case _ => ""
      }
      Await.result(f, 10 second)
    }
  }

  def changeName(userid: String, newName: String, imageUrl: Option[String]) = {
    val member = Akka.system.actorFor("/user/" + userid)
    member ! ChangeName(newName, imageUrl)
  }


  def quit(userid: String) = Akka.system.actorFor("/user/" + userid) ! Quit()

}

