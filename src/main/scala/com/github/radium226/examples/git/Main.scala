package com.github.radium226.examples.git

import java.nio.file.{Files, Path, Paths}

import cats.effect._
import org.http4s.HttpApp
import org.http4s.server.blaze.BlazeServerBuilder
import scopt.OParser
import com.github.radium226.http4s.fastcgi._

import sys.process._

import com.github.radium226.http4s.fastcgi.implicits._
import cats.implicits._
import com.github.radium226.scopt.implicits._


object Main extends IOApp {

  case class Config(folderPath: Option[Path], port: Int)

  object Config {

    def default: Config = Config(None, 8082)

  }

  def execute(command: String*): IO[Unit] = {
    IO(command !).flatMap({
      case 0 =>
        IO.unit

      case exitCode =>
        IO.raiseError(new Exception(s"${command.mkString(" ")} terminated with ${exitCode}"))
    })
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
          .action({ (folderPath, arguments) =>
            arguments.copy(folderPath = Some(folderPath))
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
      _ <- execute("git", "init", "--bare", folderPath.toString, "--shared")
      _ <- execute("git", "config", "--file", folderPath.resolve("config").toString, "http.receivepack", "true")
    } yield ()
  }

  override def run(arguments: List[String]): IO[ExitCode] = {
    for {
      config     <- loadConfig(arguments)
      folderPath <- config.folderPath.map(IO.pure).getOrElse(IO(Files.createTempDirectory("git-repo")))
      _          <- IO(println(s"folderPath=${folderPath}"))
      _          <- initRepo(folderPath)
      _          <- execute("ls", "-alrt", folderPath.toString)
      _          <- FastCGIAppBuilder[IO]
                     .withParam("SCRIPT_FILENAME", Paths.get("/usr/lib/git-core/git-http-backend"))
                     .withParam(("GIT_PROJECT_ROOT", folderPath.toString))
                     .withParam(("GIT_HTTP_EXPORT_ALL", ""))
                     .build.use(serve(config.port, _))
    } yield ExitCode.Success
  }

}
