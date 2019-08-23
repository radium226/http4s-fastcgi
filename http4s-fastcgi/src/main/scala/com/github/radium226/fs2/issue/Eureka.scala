package com.github.radium226.fs2.issue

import java.nio.file.Paths

import cats._
import cats.effect._
import fs2._
import fs2.concurrent._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s._
import cats.implicits._
import org.http4s.dsl._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.Server

import scala.concurrent.ExecutionContext

object Eureka extends IOApp {

  case class Bridge[F[_], A](queue: Queue[F, A], signal: SignallingRef[F, Boolean]) {

    def stop: F[Unit] = {
      signal.set(true)
    }

    def push1(a: A): F[Unit] = {
      queue.enqueue1(a)
    }
    def push: Pipe[F, A, Unit] = {
      queue.enqueue
    }

    def pull1: F[A] = {
      queue.dequeue1
    }

    def pull(implicit F: Concurrent[F]): Stream[F, A] = {
      queue.dequeue.interruptWhen(signal)
    }

  }

  object Bridge {

    def start[F[_], A](implicit F: Concurrent[F]): F[Bridge[F, A]] = {
      for {
        queue <- Queue.bounded[F, A](1)
        signal <- SignallingRef[F, Boolean](false)
      } yield new Bridge[F, A](queue, signal)
    }

  }



  def serve[F[_]](httpApp: HttpApp[F])(implicit F: ConcurrentEffect[F], timer: Timer[F]): F[Unit] = {
    BlazeServerBuilder[F]
      .withHttpApp(httpApp)
      .bindHttp(8080, "0.0.0.0")
      .resource.use(_ => F.never)
  }

  def makeHttpApp[F[_]](implicit F: Concurrent[F], contextShift: ContextShift[F]): F[HttpApp[F]] = {
    F.pure(HttpRoutes.of[F]({
      case GET -> Root / "show" =>
        show[F](readFile[F])
    }).orNotFound)

  }

  def readFile[F[_]](implicit F: Sync[F], contextShift: ContextShift[F]): Stream[F, Char] = {
    fs2.io.file.readAll[F](Paths.get("test.txt"), ExecutionContext.global, 1).map(_.toChar)
  }

  sealed trait End
  object End {
    case object Good extends End
    case object Bad extends End

    def parse[F[_]](char: Char)(implicit F: Sync[F]): F[End] = {
      char match {
        case '0' =>
          F.pure(Good)

        case '1' =>
          F.pure(Bad)

        case _ =>
          F.raiseError(new IllegalArgumentException)
      }
    }
  }

  def show[F[_]](chars: Stream[F, Char])(implicit F: Concurrent[F], contextShift: ContextShift[F]): F[Response[F]] = {
    for {
      responseBridge      <- Bridge.start[F, Response[F]]
      _                    = println("responseBridge created")
      bodyBridge          <- Bridge.start[F, Char]
      _                    = println("bodyBridge created")
      endBridge           <- Bridge.start[F, End]
      _                    = println("endBridge created")
      _                   <- F.start(chars.through(parse(responseBridge, bodyBridge, endBridge)).compile.drain)
      responseWithoutBody <- responseBridge.pull1
      _                    = println(s"responseWithoutBody=${responseWithoutBody}")
      response             = responseWithoutBody.withBodyStream(bodyBridge.pull.map(_.toByte))
      _                   <- F.start(endBridge.pull1.flatMap({
                               case End.Good =>
                                 println("I'm here... ")
                                 F.unit

                               case End.Bad =>
                                println("Warning, dude! ")
                                F.raiseError[Unit](new Exception("Something went wrong"))
                             }))
    } yield response
  }

  sealed trait State
  object State {
    case object Start extends State
    case class Body(length: Int) extends State
    case object End extends State
  }

  def parse[F[_]](responseBridge: Bridge[F, Response[F]], bodyBridge: Bridge[F, Char], endBridge: Bridge[F, End])(implicit F: Concurrent[F]): Pipe[F, Char, Unit] = {
    def go(state: State, chars: Stream[F, Char]): Pull[F, Unit, Unit] = {
      state match {
        case State.Start =>
          chars.pull.uncons1.flatMap({
            case Some((char, remainingChars)) =>
              val length = s"${char}".toInt
              println(s"length=${length}")
              for {
                _ <- Pull.eval(responseBridge.push1(Response[F]()))
                _ <- go(State.Body(length), remainingChars)
              } yield ()

            case None =>
              Pull.raiseError[F](new IllegalStateException("Unable to parse length"))
          })

        case State.Body(0) =>
          println(s"State.Body(0)")
          for {
            _ <- Pull.eval(bodyBridge.stop)
            _ <- go(State.End, chars)
          } yield ()

        case State.Body(length) =>
          chars.pull.uncons1.flatMap({
            case Some((char, remainingChars)) =>
              println(s"char=${char}")
              for {
                _ <- Pull.eval(bodyBridge.push1(char))
                _ <- go(State.Body(length - 1), remainingChars)
              } yield ()

            case None =>
              Pull.raiseError[F](new IllegalStateException("Unable to parse length"))
          })

        case State.End =>
          chars.pull.uncons1.flatMap({
            case Some((char, remainingChars)) =>
              println(s"[State.End] char=${char}")
              for {
                end  <- Pull.eval(End.parse(char))
                _    <- Pull.eval(endBridge.push1(end))
                _    <- Pull.done
              } yield ()

            case None =>
              Pull.raiseError[F](new IllegalStateException("Unable to parse length"))
          })
      }

    }

    chars =>
      go(State.Start, chars).stream
  }

  override def run(arguments: List[String]): IO[ExitCode] = {
    for {
      httpApp <- makeHttpApp[IO]
      _       <- serve[IO](httpApp)
    } yield ExitCode.Success

  }

}
