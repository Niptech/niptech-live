package models

import akka.actor._
import play.api.libs.json.JsObject
import play.api.Logger
import play.api.Play.current
import play.api.cache.Cache
import java.io._
import org.joda.time._

class FileWriterActor extends Actor {

  def receive = {
    case ChatMessage(msg: JsObject) =>
      Cache.getAs[String]("episodeNb").map {
        episode =>
          if (episode != "") {
            writeFile(episode, msg)
            Logger.debug("Reçu par FileWriter : l" + episode + "l MSG=>" + msg.toString)
          }
      }
  }

  def writeFile(file: String, msg: JsObject) = {
    val message = (msg \ "message").as[String]
    if (message.trim != "") {
      val now = new DateTime()
      val line = now.toString("dd/MM/YYY HH:mm:ss\t") + " " + (msg \ "user").as[String] + ": \t\t" + message
      val fw = new FileWriter("./" + file + ".txt", true)
      fw.write(line + "\n")
      fw.close()
    }
  }


}
