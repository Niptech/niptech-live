package controllers

import play.api.libs.concurrent._
import play.api.mvc._

import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.cache.Cache
import play.api.Play.current

import models._

import akka.actor._
import scala.concurrent.duration._



object Application extends Controller {

  /**
   * Just display the home page.
   */
  def index = Action {
    implicit request =>
      Ok(views.html.index())
  }

  def admin = Action {
    implicit request =>
      Ok(views.html.admin())
  }

  def initChatRoom = Action {
    Akka.system.stop(ChatRoom.default)
    ChatRoom.initialize
    Ok(views.html.msg("ChatRoom réinitialisée"))
  }

  def configure(onairswitch: Option[String], youtubeid: Option[String], twitterstreamswitch: Option[String]) = Action {
    youtubeid foreach {
      id => Cache.set("youtubeid", id)
    }
    if (twitterstreamswitch.getOrElse("off") == "on")
      Cache.set("twitterBroadcast", true)
    else
      Cache.set("twitterBroadcast", false)
    Redirect("/")
  }

  /**
   * Display the chat room page.
   */
  def chatRoom(username: Option[String], email: Option[String]) = Action {
    implicit request =>
      if (Cache.getOrElse[String]("youtubeid")("") == "")
        Redirect("/")
      else
        username.filterNot(_.isEmpty).map {
          username =>
            Ok(views.html.chatRoom(username, email.getOrElse("")))
        }.getOrElse {
          Redirect(routes.Application.index).flashing(
            "error" -> "Please choose a valid username.")
        }
  }

  /**
   * Handles the chat websocket.
   */
  def chat(username: String, email: String) = WebSocket.async[JsValue] {
    request =>
      ChatRoom.join(username, email)
  }


}
