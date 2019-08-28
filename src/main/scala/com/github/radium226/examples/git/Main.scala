package com.github.radium226.examples.git

import cats.effect._
import org.http4s.HttpApp
import org.http4s.server.blaze.BlazeServerBuilder

object Main extends IOApp {

  def serve(httpApp: HttpApp[IO]): IO[Unit] = {
    BlazeServerBuilder[IO]
      .withHttpApp(httpApp)
      .bindHttp(8080, "0.0.0.0")
      .resource.use(_ => IO.never)
  }

  override def run(arguments: List[String]): IO[ExitCode] = {
    for {

    } yield ExitCode.Success
  }

}
