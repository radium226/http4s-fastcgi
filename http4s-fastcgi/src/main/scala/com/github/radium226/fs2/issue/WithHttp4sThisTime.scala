package com.github.radium226.fs2.issue

import java.nio.file.Paths

import cats.effect._
import cats.effect.concurrent.Semaphore
import cats.implicits._
import fs2._
import fs2.concurrent._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s._

import scala.concurrent.ExecutionContext


object WithHttp4sThisTime extends IOApp {

  type Chars[F[_]] = Stream[F, Char]

  type Header = Option[Char]

  case class Parsed[F[_]](header: Header, chars: Chars[F])

  object Parsed {

    def empty[F[_]] = new Parsed[F](None, Stream.empty)

  }

  def parse[F[_]](chars: Chars[F])(implicit F: Concurrent[F]): F[Parsed[F]] = {
    def go(parsed: Parsed[F], chars: Chars[F], blocker: Semaphore[F]): Pull[F, Parsed[F], Unit] = {
      parsed match {
        case Parsed(None, _) =>
          chars.pull.uncons1.flatMap({
            case Some((char, remainingChars)) =>
              Pull.output1(Parsed[F](Some(char), remainingChars.onFinalize(F.delay(println("Releasing! ")) *> blocker.release))) >> Pull.eval(F.delay(println("Acquiring...")) *> blocker.acquire *> F.delay(println("Acquired! "))).flatMap({ _ => Pull.done})

            case None =>
              Pull.done
          })
      }
    }



    for {
      parsedQueue <- Queue.bounded[F, Parsed[F]](1)
      blocker     <- Semaphore[F](1)
      _           <- blocker.acquire
      parseFiber  <- F.start(go(Parsed.empty[F], chars, blocker).stream.take(1 + 1).through(parsedQueue.enqueue).compile.drain)
      parsed      <- parsedQueue.dequeue1
    } yield parsed
  }

  override def run(arguments: List[String]): IO[ExitCode] = {
    val httpApp = HttpApp[IO]({ request =>
      val chars = fs2.io.file.readAll[IO](Paths.get("test.txt"), ExecutionContext.global, 1).map(_.toChar)
      parse[IO](chars).map({ parsed =>
        Response[IO]()
          .withBodyStream(parsed.chars.map(_.toByte))
      })
    })

    BlazeServerBuilder[IO]
      .withHttpApp(httpApp)
      .bindHttp(8080, "0.0.0.0")
      .resource.use(_ => IO.never)
      .as(ExitCode.Success)
  }

}
