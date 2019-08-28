package com.github.radium226.examples.git

import java.nio.file.{Files, Path, Paths}

import cats.effect._
import org.http4s.HttpApp
import org.http4s.server.blaze.BlazeServerBuilder
import scopt.OParser
import com.github.radium226.http4s.fastcgi._

import sys.process._

import com.github.radium226.http4s.fastcgi.implicits._
import com.github.radium226.scopt.implicits._
import cats.implicits._


object Main extends IOApp {

  case class Config(folderPath: Path, port: Int)

  object Config {

    def default: Config = Config(null, 8080)

  }

  def loadConfig(arguments: List[String]): IO[Config] = {
    val builder = OParser.builder[Config]
    val parser = {
      import builder._
      OParser.sequence(
        opt[Int]("port")
          .action({ (port, arguments) =>
            arguments.copy(port = port)
          }),

        opt[Path]("folder")
          .required()
          .action({ (folderPath, arguments) =>
            arguments.copy(folderPath = folderPath)
          })
      )

    }

    OParser.parse[Config](parser, arguments, Config.default) match {
      case Some(arguments) =>
        IO.pure(arguments)

      case None =>
        IO.raiseError(new IllegalArgumentException)
    }
  }

  def serve(port: Int, httpApp: HttpApp[IO]): IO[Unit] = {
    BlazeServerBuilder[IO]
      .withHttpApp(httpApp)
      .bindHttp(port, "0.0.0.0")
      .resource.use(_ => IO.never)
  }

  def initRepo(folderPath: Path): IO[Unit] = {
    for {
      _ <- IO(Files.createDirectories(folderPath))
      _ <- IO(Seq("git", "init", "--bare", folderPath.resolve("git").toString, "--shared") !)
      _ <- IO(Seq("git", "config", "--file", folderPath.resolve("git").resolve("config").toString, "http.receivepack", "true") !)
    } yield ()
  }

  override def run(arguments: List[String]): IO[ExitCode] = {
    for {
      config  <- loadConfig(arguments)
      _       <- initRepo(config.folderPath)
      httpApp  = FastCGIAppBuilder[IO]
                   .withParam("SCRIPT_FILENAME", Paths.get("/usr/lib/git-core/git-http-backend"))
                   .withParam("GIT_PROJECT_ROOT", config.folderPath)
                   .withParam("GIT_EXPORT_ALL")
                   .build
      _       <- serve(config.port, httpApp)
    } yield ExitCode.Success
  }

}
