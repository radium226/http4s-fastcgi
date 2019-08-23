package com.github.radium226.fs2.issue

import cats.effect._

import fs2._
import fs2.concurrent._

import cats.implicits._


object OtherIssue extends IOApp {

  type Chars[F[_]] = Stream[F, Option[Char]]

  case class Word[F[_]](chars: Chars[F]) {

    def appendChar(char: Char): Word[F] = {
      copy(chars = chars ++ Stream.emit[F, Option[Char]](Some(char)))
    }

  }

  object Word {

    def empty[F[_]]: Word[F] = {
      Word[F](Stream.empty)
    }

  }

  case class Sentence[F[_]](chars: Chars[F]) {

    def words(implicit F: Concurrent[F]): Stream[F, Word[F]] = {
      def go(word: Word[F], chars: Chars[F], interrupter: SignallingRef[F, Boolean]): Pull[F, Word[F], Unit] = {
        chars.pull.uncons1.flatMap({
          case Some((Some(char), remainingChars)) =>
            char match {
              case ' ' =>
                Pull.output1(word.copy(chars = word.chars.onFinalize(interrupter.set(true))))

              case _ =>
                go(word.appendChar(char), remainingChars, interrupter)
            }

          case Some((None, remainingChars)) =>
            go(word, remainingChars, interrupter)
          case None =>
            Pull.output1(word.copy(chars = word.chars.onFinalize(interrupter.set(true)))) >> Pull.done
        })
      }



      Stream.eval(SignallingRef[F, Boolean](false)).flatMap({ interrupter =>
        go(Word.empty[F], chars ++ Stream.constant(None).map({ o => println(s"o=${o}") ; o }).interruptWhen(interrupter), interrupter).stream
      })
    }

  }

  object Sentence {

    def apply[F[_]](text: String)(implicit F: Concurrent[F]): Sentence[F] = {
      val chars: Chars[F] = Stream.bracket(F.delay(println("Acquiring sentence")) *> F.pure(text))({ _ => F.delay(println("Releasing sentence")) })
        .flatMap({ text =>
          Stream.emits[F, Char](text).map(Some(_)) ++ Stream.emit[F, Option[Char]](None)
        })
      new Sentence[F](chars)
    }

  }

  override def run(arguments: List[String]): IO[ExitCode] = {
    val sentence = Sentence[IO]("Kikoo Lol")
    Queue.unbounded[IO, Word[IO]]
      .flatMap({ wordQueue =>
        wordQueue.dequeue.take(1).concurrently(sentence.words.through(wordQueue.enqueue))
        .compile
        .lastOrError
      })
      .flatMap({ word =>
        word.chars.unNone.compile.fold("")(_ + _).flatMap({ wordAsString =>
          IO(println(wordAsString))
        })
      })
      .as(ExitCode.Success)
  }

}
