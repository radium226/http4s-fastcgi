package com.github.radium226.http4s.fastcgi

import java.nio.file.Path

import cats.effect._
import com.github.radium226.fastcgi.{FastCGI, FastCGIRequest}
import org.http4s._
import org.http4s.server.blaze.BlazeServerBuilder

import scala.concurrent.duration._
import scala.sys.process._

class PostMethodSpec extends FastCGISpec {

  "FastCGI" should "work with an HTTP server" in withFCGIWrap(
    s"""#!/bin/bash
       |echo "Content-Type: text/plain"
       |echo
       |cat
       |exit 0
    """.stripMargin)({ case (scriptFilePath, fastCGISocketFilePath) =>
      val fastCGI = FastCGI[IO](fastCGISocketFilePath)
      for {
        httpApp              <- makeHttpApp(fastCGI, scriptFilePath)
        _                    <- serve(httpApp)
      } yield ()
  })

  def serve(httpApp: HttpApp[IO]): IO[Unit] = {
    val port = 8080
    BlazeServerBuilder[IO]
        .withHttpApp(httpApp)
        .bindHttp(port, "0.0.0.0")
        .resource.use({ _ =>
          for {
            _ <- IO.sleep(1 second)
            _ <- IO(Seq("curl", "-X", "POST", s"http://localhost:${port}", "-d", "a" * 10000 + "k") !)
            _ <- IO.sleep(1 second)
          } yield ()
    })
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
        fastCGIRequest         <- FastCGIRequest[IO](List(
          "SCRIPT_FILENAME" -> scriptFilePath.toString, //"/usr/lib/git-core/git-http-backend",
          "REQUEST_METHOD" -> "POST",
          "QUERY_STRING" -> httpRequest.queryString
        ))
        fastCGIRequestWithBody  = fastCGIRequest.withBody(httpRequest.body)
        fastCGIResponse        <- fastCGI.run(fastCGIRequestWithBody)
        _                       = println(fastCGIResponse)
        response                = Response[IO]().withBodyStream(fastCGIResponse.body)
      } yield response
    })

  }

}
