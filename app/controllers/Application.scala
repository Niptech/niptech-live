package controllers

import play.api.libs.concurrent._
import play.api.mvc._

import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.cache.Cache
import play.api.Play.current
import play.api.Play.configuration

import models._

import akka.actor._
import scala.concurrent.duration._
import akka.pattern.{ask, pipe}
import twitter4j.{Twitter, TwitterFactory}
import play.api.mvc.Security.Authenticated
import play.api.libs.concurrent.Execution.Implicits._


import twitter4j.auth.RequestToken
import scala.util.Random
import play.api.Logger
import akka.util.Timeout


case class TwitterStore(twitter: Twitter, requestToken: RequestToken)

object Application extends Controller {

  val users = configuration.getString("authorizedUsers").map(_.split(",").toList).getOrElse(List())

  implicit val timeout = Timeout(5 second)


  def admin = Secured {
    Action {
      Ok(views.html.admin())
    }
  }

  def changeUserDialog = Action {
      Ok(views.html.changeUsername())
    }

  def configure(episodeNb: Option[String], onairswitch: Option[String], youtubeid: Option[String], twitterstreamswitch: Option[String]) = Action {
    implicit request =>
      episodeNb foreach {
        id => Cache.set("episodeNb", id)
      }
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
  def chatRoom = Action {
    implicit request =>
      val userid = request.session.get("userid").getOrElse(newUserid)
      if (Akka.system.actorFor("/user/" + userid).isTerminated)
        ChatRoom.join(userid)
      Ok(views.html.chatRoom(userid, "")) withSession ("userid" -> userid)
  }

  def newUserid = new java.util.Date().getTime().toString

  def twitterLogin = Action {
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
              val image = TwitterClient.getUserImageUrl(username)
              Cache.remove(id)
              Cache.set("@" + username, store.twitter)
              session.get("userid").map {
                userid =>
                  ChatRoom.changeName(userid, "@" + username, Some(image))
                  Redirect("/") withSession (session - "niptid")
                //Ok(views.html.chatRoom(userid, "")) withSession (session - "niptid")
              } getOrElse {
                Unauthorized("No userid connected") withNewSession
              }
          } getOrElse (Unauthorized("TwitterStore NotFound") withNewSession)
      } getOrElse (Unauthorized("No session id retrieved") withNewSession)
  }

  /**
   * Handles the chat websocket.
   */
  def chat(userid: String) = WebSocket.async[JsValue] {
    request =>
      val memberActor = Akka.system.actorFor("/user/" + userid)

      (memberActor ? Connect()).map {

        case Connected(enumerator) =>
          Logger.debug("Connecté " + userid)

          // Create an Iteratee to consume the feed
          val iteratee = Iteratee.foreach[JsValue] {
            event =>
              memberActor ! Talk((event \ "text").as[String])
          }.mapDone {
            _ =>
              memberActor ! Disconnect()
          }

          (iteratee, enumerator)

        case CannotConnect(error) =>
          Logger.debug("Erreur de connexion pour " + userid + " -> " + error)

          // Connection error
          // A finished Iteratee sending EOF
          val iteratee = Done[JsValue, Unit]((), Input.EOF)

          // Send an error and close the socket
          val enumerator = Enumerator[JsValue](JsObject(Seq("error" -> JsString(error)))).andThen(Enumerator.enumInput(Input.EOF))

          (iteratee, enumerator)

      }
  }


  def login = Action {
    Ok(views.html.login())
  }

  def doLogin = Action(parse.urlFormEncoded) {
    implicit request =>
      request.body("password").headOption match {
        case Some(password) =>
          if (users.contains(password))
            Redirect("/admin").withSession(session + ("admin", password))
          else
            Ok(views.html.login(Some("Password invalide")))
        case _ => Ok(views.html.login(Some("Password invalide")))
      }
  }

  def doLogout = Action {
    request =>
      request.session.get("userid").map {
        userid =>
          ChatRoom.quit(userid)
          Ok(views.html.msg("Tu es déconnecté de la chat room niptech")).withNewSession
      } getOrElse {
        Ok(views.html.msg("Pas d'utilisateur connecté"))
      }
  }

  def Secured(action: Action[AnyContent]) = Authenticated(
    req => req.session.get("admin"),
    _ => Forbidden(views.html.login(Some("Il faut être connecté pour accéder à la page d'administration")))
  )(username => action)

}
