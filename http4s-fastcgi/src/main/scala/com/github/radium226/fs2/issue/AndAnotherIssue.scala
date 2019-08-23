package com.github.radium226.fs2.issue

import java.nio.file.Paths

import cats.effect._
import cats.effect.concurrent.{MVar, Semaphore}
import cats.implicits._
import fs2._
import fs2.concurrent._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext


object AndAnotherIssue extends IOApp {

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
              println("We are here")
              Pull.output1(Parsed[F](Some(char), remainingChars.onFinalize(F.delay(println("Releasing! ")) *> blocker.release))) >> Pull.eval(F.delay(println("Acquiring...")) *> blocker.acquire *> F.delay(println("Acquired! "))).flatMap({ _ => Pull.done})

            case None =>
              println("We should not be here")
              Pull.done
          })
      }
    }



    for {
      parsedQueue <- Queue.bounded[F, Parsed[F]](1)
      blocker     <- Semaphore[F](1)
      _           <- blocker.acquire
      parseFiber  <- F.start(go(Parsed.empty[F], chars, blocker).stream.map({t => println(t) ; t }).take(1 + 1).through(parsedQueue.enqueue).compile.drain)
      parsed      <- parsedQueue.dequeue1
      _           <- F.start(parseFiber.join *> F.delay(println("Joined! ")))
    } yield parsed
  }

  override def run(arguments: List[String]): IO[ExitCode] = {
    println("Yay")
    val chars = fs2.io.file.readAll[IO](Paths.get("test.txt"), ExecutionContext.global, 1).map(_.toChar)

    for {
      parsed <- parse[IO](chars)
      chars  <- parsed.chars.compile.lastOrError
      _       = println(s"chars=${chars}")
    } yield ExitCode.Success


  }

}
