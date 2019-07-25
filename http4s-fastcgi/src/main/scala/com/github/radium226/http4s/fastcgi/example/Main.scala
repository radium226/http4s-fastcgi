package com.github.radium226.http4s.fastcgi.example

import org.http4s.dsl.io._
import org.http4s.headers.Authorization
import org.http4s.server.blaze._
import org.http4s.implicits._
import org.http4s.server._
import org.http4s._
import cats.effect._
import cats.implicits._
import fs2._
import com.github.radium226.fs2.io.process
import com.github.radium226.http4s.fs2.PipeRoutes

import org.apache.commons.codec.binary.Hex

import scala.concurrent.ExecutionContext

object Main extends IOApp {

  override def run(arguments: List[String]): IO[ExitCode] = {
    val pipe: Pipe[IO, Request[IO], Response[IO]] = { requests =>
      requests
        .map({ request =>
          val t = request.pathInfo
          println(t)
          s"${t}\n"
        })
        .through(fs2.text.utf8Encode[IO])
        .map({ t => println(t) ; t })
        .through(process.pipe[IO](List("tr", "[:lower:]", "[:upper:]")))
        .map({ t => println(t) ; t })
        .through(fs2.text.utf8Decode[IO])
        .map({ t => println(t) ; t })
        .through(fs2.text.lines[IO])
        .map({ t => println(t) ; t })
        .map({ line =>
          println(line)
          Response[IO](Status.Ok).withEntity(line)
        })
        .drain
    }

    (for {
      routes       <- PipeRoutes[IO](pipe)
      serverBuilder = BlazeServerBuilder[IO]
          .withHttpApp(routes.orNotFound)
          .bindHttp(8080, "0.0.0.0")
      _            <- serverBuilder.resource.use(_ => IO.never)
    } yield ()).as(ExitCode.Success)


  }

}
