package com.github.radium226.http4s.fastcgi

import java.nio.file.Path

import cats.effect._
import org.http4s._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.Router

import org.http4s.implicits._
import com.github.radium226.fastcgi.implicits._
import com.github.radium226.http4s.implicits._


class HttpServerWithMountedRouteSpec extends FastCGISpec {

  "FastCGI" should "work with an HTTP server having mounted routes" in withFCGIWrap(
    s"""#!/bin/bash
       |echo "Content-Type: text/plain"
       |echo ""
       |echo "Hello!"
       |exit 0
    """.stripMargin)({ case (scriptFilePath, fastCGISocketFilePath) =>
    val httpApp = makeHttpApp(fastCGISocketFilePath, scriptFilePath)
    val router = Router[IO](
      "/hello" -> httpApp.routes
    )
    serve(router.orNotFound)
  })

  def serve(httpApp: HttpApp[IO]): IO[Unit] = {
    BlazeServerBuilder[IO]
        .withHttpApp(httpApp)
        .bindHttp(8080, "0.0.0.0")
        .resource.use(_ => IO.never)
  }

  def makeHttpApp(socketFilePath: Path, scriptFilePath: Path): HttpApp[IO] = {
    FastCGIAppBuilder[IO]
      .withSocket(socketFilePath)
      .withParam("SCRIPT_FILENAME", scriptFilePath)
      .build
  }

}
