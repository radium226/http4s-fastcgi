import sbt._

object Dependencies {

  lazy val cats = Seq(
    "org.typelevel" %% "cats-core" % "2.0.0",
    "org.typelevel" %% "cats-effect" % "2.0.0"
  )

  lazy val dbus = Seq(
    "com.github.hypfvieh" % "dbus-java" % "3.0.0"
  )

  lazy val fs2 = Seq(
    "co.fs2" %% "fs2-core" % "2.0.1",
    "co.fs2" %% "fs2-io" % "2.0.1"
  )

  lazy val logback = Seq(
    "ch.qos.logback" % "logback-classic" % "1.2.3"
  )

  lazy val config = Seq(
    "com.typesafe" % "config" % "1.3.4",
    "com.iheart" %% "ficus" % "1.4.7"
  )

  lazy val scopt = Seq(
    "com.github.scopt" %% "scopt" % "4.0.0-RC2"
  )

  lazy val akkaStream = Seq(
    "com.typesafe.akka" %% "akka-stream" % "2.5.26",
    "com.typesafe.akka" %% "akka-stream-contrib" % "2.5.26",
    "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.26" % Test
  )

  lazy val http4s = Seq(
    "org.http4s" %% "http4s-core" % "0.21.0-M5",
    "org.http4s" %% "http4s-dsl" % "0.21.0-M5",
    "org.http4s" %% "http4s-blaze-server" % "0.21.0-M5",
    "org.http4s" %% "http4s-blaze-client" % "0.21.0-M5"
  )

  lazy val xtract = Seq(
    "com.lucidchart" %% "xtract" % "2.2.1"
  )

  lazy val libpam4j = Seq(
    "org.kohsuke" % "libpam4j" % "1.11"
  )

  lazy val scalatic = Seq(
    "org.scalactic" %% "scalactic" % "3.0.8"
  )

  lazy val scalaTest = Seq(
    "org.scalatest" %% "scalatest" % "3.0.8"
  )

  lazy val guava = Seq(
    "com.google.guava" % "guava" % "28.0-jre"
  )

  lazy val circe = Seq(
    "io.circe" %% "circe-core" % "0.12.3",
    "io.circe" %% "circe-parser" % "0.12.3"
  )

  lazy val nuProcess = Seq(
    "com.zaxxer" % "nuprocess" % "1.2.4"
  )

  lazy val apacheCommons = Seq(
    "commons-codec" % "commons-codec" % "1.12",
    "commons-io" % "commons-io" % "2.6"
  )

  lazy val jUnixSocket = Seq(
    "com.kohlschutter.junixsocket" % "junixsocket-core" % "2.2.0"
  )

  lazy val scallop = Seq(
    "org.rogach" %% "scallop" % "3.3.1"
  )

}
