import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "niptech-live"
  val appVersion      = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    // Add your project dependencies here,
    "org.twitter4j" % "twitter4j-core" % "3.0.4-SNAPSHOT",
    "org.twitter4j" % "twitter4j-stream" % "3.0.4-SNAPSHOT",
    "org.webjars" %% "webjars-play" % "2.1.0-2",
    "org.webjars" % "angularjs" % "1.1.5-1",
    "org.webjars" % "bootstrap" % "2.3.2",
    "org.scalatest" %% "scalatest" % "1.9.1" % "test",
    "junit" % "junit" % "4.11" % "test",
    "com.novocode" % "junit-interface" % "0.7" % "test->default"
  )


  val main = play.Project(appName, appVersion, appDependencies).settings(
    // Add your own project settings here
    resolvers += "Twitter4J" at "http://twitter4j.org/maven2"
  )

}
