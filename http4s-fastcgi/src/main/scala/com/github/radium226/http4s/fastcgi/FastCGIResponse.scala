package com.github.radium226.http4s.fastcgi

import java.util

import cats.effect._
import fs2._
import cats.implicits._

import org.http4s._

case class FastCGIResponse[F[_]](headers: FastCGIHeaders, body: FastCGIBody[F]) {

  def withHeader(header: FastCGIHeader): FastCGIResponse[F] = {
    copy(headers = headers :+ header)
  }

  def withBody(body: FastCGIBody[F]): FastCGIResponse[F] = {
    copy(body = body)
  }

  def unwrap(implicit F: Sync[F]): Response[F] = {
    Response[F](
      headers = Headers(headers.map({ case (name, value) => Header(name, value).parsed })),
      body = body
    )
  }

}

object FastCGIResponse {

  def headerReaders[F[_]] = List[FastCGIParamReader[Response[F]]](
    { case ("STATUS", value) => _.withStatus(Status(code = value.toInt)) }
  )

  def empty[F[_]]: FastCGIResponse[F] = {
    new FastCGIResponse[F](
      headers = FastCGIHeaders.empty,
      body = FastCGIBody.empty
    )
  }

  private[fastcgi] def pushResponseWithBodyAndResponseBody[F[_]](responseWithoutBodyBridge: Bridge[F, FastCGIResponse[F]], responseBodyBridge: Bridge[F, Byte])(implicit F: Concurrent[F]): Pipe[F, Byte, Unit] = {
    sealed trait State

    object State {

      type ParamKey = String

      type ParamValue = String

      case object Beginning extends State

      case class Param(length: Int, response: FastCGIResponse[F], key: Option[ParamKey], value: Option[ParamValue]) extends State {

        def appendToKey(char: Char): Param = {
          copy(length = length - 1, key = key.map({ chars => Some(chars :+ char) }).getOrElse(Some(s"${char}")))
        }

        def appendToValue(char: Char): Param = {
          copy(length = length - 1, value = value.map({ chars => Some(chars :+ char) }).getOrElse(Some(s"${char}")))
        }

        def empty(length: Int): Param = {
          copy(length = length, response = response.withHeader(key.get, value.get), key = None, value = None)
        }

      }

      case class Stdout(length: Int) extends State {

        def readChar(char: Char): Stdout = {
          copy(length = length - 1)
        }

      }

      case class Body(length: Int) extends State

    }

    def go(stream: Stream[F, Byte], state: State): Pull[F, Unit, Unit] = {
      state match {
        case State.Beginning =>
          stream.chunkN(8, true).map(_.toArray).pull.uncons1.flatMap({
            case Some((bytes, stream)) =>
              val version = bytes(0)
              val `type` = bytes(1)
              val id = ((bytes(2) << 8) + bytes(3)) & 0xff
              val length = ((bytes(4) << 8) + bytes(5)) & 0xff
              val padding = bytes(6)
              println(s"version=${version}, `type`=${`type`}, id=${id}, length=${length}, padding=${padding}")
              `type` match {
                case FastCGI.stdout =>
                  go(stream.flatMap({ bytes => Stream.chunk(Chunk.array(bytes)) }), State.Stdout(length))
              }
            case None =>
              Pull.raiseError[F](new IllegalStateException)
          })

        case State.Stdout(length) =>
          go(stream, State.Param(length, FastCGIResponse.empty[F], None, None))


        case State.Param(length, response, Some(key), None) =>
          stream.pull.uncons1.flatMap({
            case Some((byte, stream)) =>
              byte.toChar match {
                case ':' =>
                  go(stream, State.Param(length - 1, response, Some(key), Some("")))

                case char =>
                  go(stream, State.Param(length - 1, response, Some(s"${key}${char}"), None))
              }
            case None =>
              Pull.raiseError[F](new IllegalArgumentException)
          })

        case State.Param(length, response, None, None) =>
          stream.pull.uncons1.flatMap({
            case Some((byte, stream)) =>
              println(s"byte=${byte}")
              byte.toChar match {
                case '\r' =>
                  for {
                    _ <- Pull.eval(responseWithoutBodyBridge.push1(response))
                    _ <- go(stream.drop(1), State.Body(length - (1 + 1)))
                  } yield ()
                case char =>
                  println(s"char=${char}")
                  go(stream, State.Param(length - 1, response, Some(s"${char}"), None))
              }
            case None =>
              Pull.raiseError[F](new IllegalStateException())
          })
        // Parsing header value
        case State.Param(length, response, Some(name), Some(value)) =>
          stream.pull.uncons1.flatMap({
            case Some((byte, stream)) =>
              byte.toChar match {
                case cr @ ('\r' | '\n') =>
                  cr match {
                    case '\r' =>
                      println("\\r")
                    case '\n' =>
                      println("\\n")
                  }
                  go(stream.drop(1), State.Param(length - (1 + 1), response.withHeader(name -> value), None, None))
                case char =>
                  go(stream, State.Param(length - 1, response, Some(name), Some(value :+ char)))
              }
            case None =>
              Pull.raiseError[F](new IllegalStateException())
          })

        case State.Body(0) =>
          /*for {
            _ <- Pull.eval(responseBodyBridge.stop)
            _ <- Pull.done
          } yield ()*/
          go(stream, State.Beginning)

        case State.Body(length) =>
          stream.pull.uncons1.flatMap({
            case Some((byte, stream)) =>
              for {
                _ <- Pull.eval(responseBodyBridge.push1(byte))
                _ <- go(stream, State.Body(length - 1))
              } yield ()
            case None =>
              Pull.raiseError[F](new IllegalStateException())
          })

      }
    }

    { bytes =>
      go(bytes, State.Beginning).stream
    }
  }

  def parse[F[_]](bytes: Stream[F, Byte])(implicit F: Concurrent[F]): F[FastCGIResponse[F]] = {
    for {
      responseWithoutBodyBridge <- Bridge.start[F, FastCGIResponse[F]]
      responseBodyBridge        <- Bridge.start[F, Byte]
      _                         <- F.start(bytes.through(pushResponseWithBodyAndResponseBody(responseWithoutBodyBridge, responseBodyBridge)).compile.drain)
      responseWithoutBody       <- responseWithoutBodyBridge.pull1
      responseBody               = responseBodyBridge.pull
      response                   = responseWithoutBody.withBody(responseBody)
    } yield response
  }

}
