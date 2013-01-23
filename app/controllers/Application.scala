package controllers

import play.api._
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
  def index = Action { implicit request =>
    Ok(views.html.index())
  }

  def goLive(videoId: String) = Action {
    Cache.set("youtubeid", videoId)
    Ok(views.html.msg("Niptech live connecté sur l'id YouTube : " + videoId))
  }

  def stopLive() = Action {
    Cache.set("youtubeid", "")
    Ok(views.html.msg("Niptech live déconnecté"))
  }

  /**
   * Display the chat room page.
   */
  def chatRoom(username: Option[String]) = Action { implicit request =>
    username.filterNot(_.isEmpty).map { username =>
      Ok(views.html.chatRoom(username))
    }.getOrElse {
      Redirect(routes.Application.index).flashing(
        "error" -> "Please choose a valid username.")
    }
  }

  /**
   * Handles the chat websocket.
   */
  def chat(username: String) = WebSocket.async[JsValue] { request =>

    ChatRoom.join(username)

  }

}
