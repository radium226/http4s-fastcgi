package com.github.radium226.http4s.fastcgi

import java.nio.file.Path

import cats.effect._
import com.github.radium226.fastcgi.{FastCGI, FastCGIParam, FastCGIRequest}
import org.http4s._
import org.http4s.server.blaze.BlazeServerBuilder

import scala.concurrent.duration._
import scala.sys.process._

class GitWithHttpServerAndFastCGIAppBuilderSpec extends FastCGISpec {

  "FastCGI" should "work with an HTTP server" in withFCGIWrap(
    s"""#!/bin/bash
       |echo "Content-Type: text/plain"
       |echo
       |env
       |exit 0
    """.stripMargin)({ case (scriptFilePath, fastCGISocketFilePath) =>
      for {
        remoteRepoFolderPath <- initRepo()
        _                    <- makeHttpApp(fastCGISocketFilePath, scriptFilePath, remoteRepoFolderPath).use(serve)
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

  def makeHttpApp(socketFilePath: Path, scriptFilePath: Path, remoteRepoFolderPath: Path): Resource[IO, HttpApp[IO]] = {
    /*IO.pure(HttpApp[IO] { httpRequest =>
      for {
        fastCGIRequest  <- FastCGIRequest.wrap[IO](scriptFilePath)(httpRequest)
        fastCGIResponse <- fastCGI.run(fastCGIRequest)
      } yield fastCGIResponse.unwrap
    })*/
    val fastCGIAppBuilder = FastCGIAppBuilder[IO]
        .withParam("SCRIPT_FILENAME" -> /*scriptFilePath.toString, */"/usr/lib/git-core/git-http-backend")
        .withParam("GIT_PROJECT_ROOT" -> remoteRepoFolderPath.toString)
        .withParam("GIT_HTTP_EXPORT_ALL" -> "")
    fastCGIAppBuilder.build
  }

}
