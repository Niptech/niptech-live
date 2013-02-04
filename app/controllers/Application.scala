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
import twitter4j.{Twitter, TwitterFactory}
import twitter4j.auth.RequestToken
import scala.util.Random
import play.api.Logger


case class TwitterStore(twitter: Twitter, requestToken: RequestToken)

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


  def configure(onairswitch: Option[String], youtubeid: Option[String], twitterstreamswitch: Option[String]) = Action {
    youtubeid foreach {
      id => Cache.set("youtubeid", id)
      if (id == "") ChatRoom.clean else ChatRoom.initialize
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

  def twitterLogin(username: Option[String], email: Option[String]) = Action {
    implicit request =>
      val twitter = TwitterClient.newInstance
      val id = new Random(new java.util.Date().getTime()).nextLong().abs.toString
      val requestToken = twitter.getOAuthRequestToken("http://live.niptech.com/callback")
      Cache.set(id, TwitterStore(twitter, requestToken))
      Redirect(requestToken.getAuthenticationURL) withSession (session + ("niptid" -> id))
  }

  def callback(oauth_token: String, oauth_verifier: String) = Action {
    implicit request =>
      session.get("niptid").map {
        id =>
          Cache.get(id).map {
            storeRef =>
              val store = storeRef.asInstanceOf[TwitterStore]
              store.twitter.getOAuthAccessToken(store.requestToken, oauth_verifier)
              val username = store.twitter.getScreenName
              Cache.remove(id)
              Cache.set("@" + username, store.twitter)
              Redirect(routes.Application.chatRoom(Some("@" + username), Some(username))) withNewSession
          } getOrElse (Unauthorized("TwitterStore NotFound") withNewSession)
      } getOrElse (Unauthorized("No session id retrieved") withNewSession)
  }

  /**
   * Handles the chat websocket.
   */
  def chat(username: String, email: String) = WebSocket.async[JsValue] {
    request =>
      ChatRoom.join(username, email)
  }


}
