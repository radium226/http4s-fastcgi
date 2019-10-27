import sbt.Keys.libraryDependencies

logLevel := Level.Debug

ThisBuild / organization := "com.github.radium226"
ThisBuild / scalaVersion := "2.13.1"
ThisBuild / version      := "0.1-SNAPSHOT"

ThisBuild / scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-language:postfixOps",
  "-language:higherKinds")

lazy val `system` = RootProject(file("../system-scala"))

lazy val `root` = (project in file("."))
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
    libraryDependencies ++= Dependencies.scopt,

    resolvers += Resolver.sonatypeRepo("releases"),
    resolvers += Resolver.mavenLocal,

    logBuffered in Test := false,

    addCompilerPlugin(CompilerPlugins.kindProjector)
  )
  .dependsOn(`system`)
