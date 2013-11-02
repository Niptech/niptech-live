package models

import play.api.Play.configuration
import play.api.Play.current

import twitter4j.{TwitterStreamFactory, TwitterFactory, Twitter}
import twitter4j.conf.ConfigurationBuilder


/**
 * Created with IntelliJ IDEA.
 * User: croiseaux
 * Date: 26/01/13
 * Time: 12:26
 * To change this template use File | Settings | File Templates.
 */
object TwitterClient {

  val defaultImageUrl = "http://www.gravatar.com/avatar/none?s=20"


  val isValid = configuration.getString("twitter.ConsumerKey").getOrElse("").length > 0 &&
    configuration.getString("twitter.ConsumerSecret").getOrElse("").length > 0 &&
    configuration.getString("twitter.AccessToken").getOrElse("").length > 0 &&
    configuration.getString("twitter.AccessTokenSecret").getOrElse("").length > 0


  val twitterConfiguration = if (isValid){
    val cb = new ConfigurationBuilder()

    cb.setDebugEnabled(true)
      .setOAuthConsumerKey(configuration.getString("twitter.ConsumerKey").get)
      .setOAuthConsumerSecret(configuration.getString("twitter.ConsumerSecret").get)
      .setOAuthAccessToken(configuration.getString("twitter.AccessToken").get)
      .setOAuthAccessTokenSecret(configuration.getString("twitter.AccessTokenSecret").get)

    cb.build

  }else{

    new ConfigurationBuilder().build()
  }

  val twitter = {
    new TwitterFactory(twitterConfiguration).getInstance()
  }

  def newInstance = {
    val tw = new TwitterFactory().getInstance()
    tw.setOAuthConsumer(configuration.getString("twitter.ConsumerKey").get, configuration.getString("twitter.ConsumerSecret").get)
    tw
  }

  val twitterStream = {
    new TwitterStreamFactory(twitterConfiguration).getInstance()
  }

  def getUserImageUrl(user: String) = try {
    twitter.showUser(user).getMiniProfileImageURL
  } catch {
    case exc: Throwable => defaultImageUrl
  }

}
