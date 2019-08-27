package com.github.radium226.http4s.fastcgi

import java.nio.file.Path

import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server.blaze.BlazeServerBuilder
import cats.effect.implicits._
import org.http4s.implicits._

class HttpServerSpec extends FastCGISpec {

  "FastCGI" should "work with an HTTP server" in withFCGIWrap(
    s"""#!/bin/bash
      |
      |echo "Content-type: text/html"
      |echo ""
      |echo '<html>'
      |echo '<head>'
      |echo '<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">'
      |echo '<title>Hello World</title>'
      |echo '</head>'
      |echo '<body>'
      |${"echo 'Hello World!' ; " * 100}
      |echo '</body>'
      |echo '</html>'
      |exit 0
    """.stripMargin)({ case (scriptFilePath, fastCGISocketFilePath) =>
      val fastCGI = FastCGI[IO](fastCGISocketFilePath)
      for {
        httpApp <- makeHttpApp(fastCGI, scriptFilePath)
        _       <- serve(httpApp)
      } yield ()
  })

  def serve(httpApp: HttpApp[IO]): IO[Unit] = {
    BlazeServerBuilder[IO]
      .withHttpApp(httpApp)
      .bindHttp(8080, "0.0.0.0")
      .resource.use(_ => IO.never)
  }

  def makeHttpApp(fastCGI: FastCGI[IO], scriptFilePath: Path): IO[HttpApp[IO]] = {
    /*IO.pure(HttpApp[IO] { httpRequest =>
      for {
        fastCGIRequest  <- FastCGIRequest.wrap[IO](scriptFilePath)(httpRequest)
        fastCGIResponse <- fastCGI.run(fastCGIRequest)
      } yield fastCGIResponse.unwrap
    })*/

    IO.pure(HttpApp[IO] { httpRequest =>
      for {
        fastCGIRequest  <- FastCGIRequest[IO](List("SCRIPT_FILENAME" -> scriptFilePath.toString))
        fastCGIResponse <- fastCGI.run(fastCGIRequest)
        response         = Response[IO]().withBodyStream(fastCGIResponse.body.map({ t => println(t) ; t }))
      } yield response
    })

  }

}
