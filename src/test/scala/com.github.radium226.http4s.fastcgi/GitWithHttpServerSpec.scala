package com.github.radium226.http4s.fastcgi

import java.nio.file.Path

import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server.blaze.BlazeServerBuilder
import cats.effect.implicits._
import com.github.radium226.fastcgi.{FastCGI, FastCGIParam, FastCGIRequest}
import org.http4s.implicits._

import scala.concurrent.duration._
import sys.process._

class GitWithHttpServerSpec extends FastCGISpec {

  "FastCGI" should "work with an HTTP server" in withFCGIWrap(
    s"""#!/bin/bash
       |echo "Content-Type: text/plain"
       |echo
       |env
       |exit 0
    """.stripMargin)({ case (scriptFilePath, fastCGISocketFilePath) =>
      val fastCGI = FastCGI[IO](fastCGISocketFilePath)
      for {
        remoteRepoFolderPath <- initRepo()
        httpApp              <- makeHttpApp(fastCGI, scriptFilePath, remoteRepoFolderPath)
        _                    <- serve(httpApp)
      } yield ()
  })

  def serve(httpApp: HttpApp[IO]): IO[Unit] = {
    val port = 8080
    BlazeServerBuilder[IO]
        .withHttpApp(httpApp)
        .bindHttp(port, "0.0.0.0")
        .resource.use( { _ =>
      for {
        _               <- IO.sleep(1 second)
        localFolderPath <- cloneRepo("localhost", port)
        _                = println(s"toto=${localFolderPath}")
        _               <- IO.sleep(1 second)
        _               <- IO(Seq("bash", "-c", s"cd '${localFolderPath.toString}' && echo 'WATCHA' && ls -alrt && echo 'TOTO' >'TOTO' && git add 'TOTO' && git commit -m 'Add TOTO' && git push -u origin master") !)
        //_               <- IO.sleep(1 minute)
      } yield ()
    })
  }

  def makeHttpApp(fastCGI: FastCGI[IO], scriptFilePath: Path, remoteRepoFolderPath: Path): IO[HttpApp[IO]] = {
    /*IO.pure(HttpApp[IO] { httpRequest =>
      for {
        fastCGIRequest  <- FastCGIRequest.wrap[IO](scriptFilePath)(httpRequest)
        fastCGIResponse <- fastCGI.run(fastCGIRequest)
      } yield fastCGIResponse.unwrap
    })*/

    IO.pure(HttpApp[IO] { httpRequest =>
      for {
        fastCGIRequest         <- FastCGIRequest[IO](List(
          "SCRIPT_FILENAME" -> /*scriptFilePath.toString, */"/usr/lib/git-core/git-http-backend",
          "REQUEST_METHOD" -> httpRequest.method.name,
          "GIT_PROJECT_ROOT" -> remoteRepoFolderPath.toString,
          "PATH_INFO" -> httpRequest.pathInfo,
          "GIT_HTTP_EXPORT_ALL" -> "",
          "QUERY_STRING" -> httpRequest.queryString
        ) ++ httpRequest.contentType.map({ ct => List("CONTENT_TYPE" -> ct.value) }).getOrElse(List.empty[FastCGIParam])
        )
        fastCGIRequestWithBody  = fastCGIRequest.withBody(httpRequest.body)
        fastCGIResponse        <- fastCGI.run(fastCGIRequestWithBody)
        _                       = println(fastCGIResponse)
        response                = Response[IO]()
            .withBodyStream(fastCGIResponse.body)
            .withHeaders(fastCGIResponse.headers.map({ case (name, value) => Header(name, value).parsed }): _*)
        _ = println(response)

      } yield response
    })

  }

}
