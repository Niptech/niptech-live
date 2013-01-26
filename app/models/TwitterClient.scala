package models

import twitter4j.TwitterFactory
import twitter4j.Twitter
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

  val twitter = {
    val cb = new ConfigurationBuilder()

    cb.setDebugEnabled(true)
      .setOAuthConsumerKey("caUDPvFcgbWBgkV4TbzUkw")
      .setOAuthConsumerSecret("LJDnxYJmaCh7NL6FsZnAR1S35djb0bIvaUcV4OeHM")
      .setOAuthAccessToken("1121664438-NvyGMZDkpbcDqon4VD91VMBA0FoJyql3ivP8T93")
      .setOAuthAccessTokenSecret("gdj7U0lePdGA1pB1TZNsfqoyvkAUCN7AqMkYUs9kpec")

    val tf = new TwitterFactory(cb.build())
    tf.getInstance()
  }

  def getUserImageUrl(user: String) = try {twitter.showUser(user).getMiniProfileImageURL} catch {case exc => defaultImageUrl}

}
