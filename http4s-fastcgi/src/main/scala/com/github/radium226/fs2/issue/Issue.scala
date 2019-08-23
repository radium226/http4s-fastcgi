package com.github.radium226.fs2.issue

import java.nio.file.{Files, Paths}

import cats.effect._
import fs2._
import cats.implicits._
import org.http4s._
import org.http4s.server.blaze.BlazeServerBuilder

import scala.concurrent.ExecutionContext

object Issue extends IOApp {

  type Kind = (Int, Int, Int)

  type Input[F[_]] = Stream[F, Char]

  type Content[F[_]] = Stream[F, Char]

  case class Output[F[_]](kind: Kind, content: Content[F])

  def run[F[_]](input: Input[F])(implicit F: Concurrent[F]): Stream[F, Output[F]] = {
    def go(output: Output[F], input: Input[F]): Pull[F, Output[F], Unit] = {
      output match {
        case Output((0, 0, 0), _) =>
          input.chunkN(3, true).map(_.toArray).pull.uncons1.flatMap({
            case Some((array, remainingArrays)) =>
              val kind = (s"${array(0)}".toInt, s"${array(1)}".toInt, s"${array(2)}".toInt)

              val remainingInput = remainingArrays.flatMap({ ra => Stream.chunk(Chunk.array(ra)) })
              go(output.copy(kind = kind), remainingInput)

            case None =>
              Pull.done
          })

        case output @ Output(_, _) =>
          Pull.output1(output.copy(content = input))
      }
    }

    go(Output((0, 0, 0), Stream.empty), input).stream
  }

  def execute[F[_], A](input: Input[F])(f: Output[F] => F[A])(implicit F: Concurrent[F]): F[A] = {
    run(input).evalMap(f(_)).take(1).compile.lastOrError
  }

  override def run(arguments: List[String]): IO[ExitCode] = {
    val httpApp = HttpApp[IO]({ request =>
      IO({
        val path = request.pathInfo
        Response[IO]()
          .withStatus(Status.Ok)
          .withBodyStream(Stream.emits[IO, Byte](path.map(_.toByte)))
      })

      /*val input: Input[IO] = (Stream.bracket(IO.delay(fs2.io.readInputStream(IO(Files.newInputStream(Paths.get("test.txt"))), 1, ExecutionContext.global).map(_.toChar).map({ c => println(s"c=${c}") ; c })))({ _ => IO(println("Closed"))})).flatMap({ stream => stream })
      execute(input) { output =>
        IO(Response[IO](status = Status.Ok, body = output.content.map(_.toByte)))
      }*/
    })

    BlazeServerBuilder[IO]
        .withHttpApp(httpApp)
        .bindHttp(8080, "0.0.0.0")
        .resource.use(_ => IO.never)
        .as(ExitCode.Success)

  }

}
