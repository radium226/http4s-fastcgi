package com.github.radium226.http4s.fastcgi

import java.nio.charset.StandardCharsets

import fs2._
import cats.effect._
import cats.implicits._

class PullSpec extends FastCGISpec {

  type Key = String

  type Value = String

  type Header = (Key, Value)

  type Headers = List[Header]

  type Response[F[_]] = (Headers, Stream[F, Byte])

  object Response {

    def empty[F[_]]: Response[F] = (List.empty, Stream.empty)

  }

  it should "be able to parse one by one" in {
    sealed trait State
    case object State {
      case object initialize extends State
      case class choose(char: Char) extends State
      case class header(key: Option[Key], value: Option[Value]) extends State
      case object content extends State
      case class skip(count: Int) extends State
    }

    val text =
      """Key1: Value2
        |Key2: Value2
        |
        |ContentContentContent
        |""".stripMargin

    def go(state: State, response: Response[IO], stream: Stream[IO, Byte]): Pull[IO, Response[IO], Unit] = {
      state match {
        case State.initialize =>
          stream.pull.uncons1.flatMap({
            case Some((byte, stream)) =>
              go(State.choose(byte.toChar), response, stream)

            case None =>
              Pull.done
          })

        case State.choose(char) =>
          char match {
            case '\r' | '\n' =>
              Pull.output1((response._1, stream))

            case _ =>
              go(State.header(Some(s"${char}"), None), response, stream)
          }

        // Parsing header key
        case State.header(Some(key), None) =>
          stream.pull.uncons1.flatMap({
            case Some((byte, stream)) =>
              byte.toChar match {
                case ':' =>
                  go(State.header(Some(key), Some("")), response, stream)
                case char =>
                  go(State.header(Some(key + char), None), response, stream)
              }
            case None =>
              Pull.done
          })

        // Parsing header value
        case State.header(Some(key), Some(value)) =>
          stream.pull.uncons1.flatMap({
            case Some((byte, stream)) =>
              byte.toChar match {
                case '\r' | '\n' =>
                  go(State.initialize, (response._1 :+ (key.trim -> value.trim), response._2), stream)
                case char =>
                  go(State.header(Some(key), Some(value + char)), response, stream)
              }
            case None =>
              Pull.done
          })
      }
    }

    val byteStream: Stream[IO, Byte] = Stream(text.getBytes(StandardCharsets.UTF_8): _*)
    val responseStream = go(State.initialize, Response.empty[IO], byteStream).stream

    val responses = responseStream.compile.toList.unsafeRunSync

    responses
      .foreach({ case (headers, content) =>
        println(s"headers=${headers}")
          println(s"content=${new String(content.compile.toList.unsafeRunSync.toArray)}")
      })
  }

}
