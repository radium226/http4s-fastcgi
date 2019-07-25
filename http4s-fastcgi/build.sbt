import sbt.Keys.libraryDependencies

logLevel := Level.Debug

ThisBuild / organization := "com.github.radium226"
ThisBuild / scalaVersion := "2.12.7"
ThisBuild / version      := "0.1-SNAPSHOT"

ThisBuild / scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-language:postfixOps",
  "-language:higherKinds",
  "-Ypartial-unification")

lazy val root = (project in file("."))
  .settings(
    name := "http4s-fastcgi",

    libraryDependencies ++= Dependencies.scalatic,
    libraryDependencies ++= Dependencies.scalaTest map(_ % Test),
    libraryDependencies ++= Dependencies.cats,
    libraryDependencies ++= Dependencies.http4s,
    libraryDependencies ++= Dependencies.logback,
    libraryDependencies ++= Dependencies.nuProcess,
    libraryDependencies ++= Dependencies.apacheCommons,
    libraryDependencies ++= Dependencies.guava,
    libraryDependencies ++= Dependencies.jUnixSocket,

    // The main goal here: remove this old library dependency
    libraryDependencies += "org.jfastcgi.client" % "client-core" % "2.4-SNAPSHOT",

    resolvers += Resolver.sonatypeRepo("releases"),
    resolvers += Resolver.mavenLocal,

    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.8"),
    logBuffered in Test := false,
  )
