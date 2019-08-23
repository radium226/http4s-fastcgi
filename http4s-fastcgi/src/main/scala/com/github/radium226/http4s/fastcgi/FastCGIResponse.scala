package com.github.radium226.http4s.fastcgi

import java.util

import cats.effect._
import fs2._

import cats.implicits._

case class FastCGIResponse[F[_]](headers: FastCGIHeaders, body: FastCGIBody[F]) {

  def withHeader(header: FastCGIHeader): FastCGIResponse[F] = {
    copy(headers = headers :+ header)
  }

  def withBody(body: FastCGIBody[F]): FastCGIResponse[F] = {
    copy(body = body)
  }

}

object FastCGIResponse {

  def empty[F[_]]: FastCGIResponse[F] = {
    new FastCGIResponse[F](
      headers = FastCGIHeaders.empty,
      body = FastCGIBody.empty
    )
  }

  private[fastcgi] def pushResponseWithBodyAndResponseBody[F[_]](responseWithoutBodyBridge: Bridge[F, FastCGIResponse[F]], responseBodyBridge: Bridge[F, Byte]): Pipe[F, Byte, Unit] = {
    ???
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

  def pipe[F[_]](implicit F: Concurrent[F]): Pipe[F, Byte, FastCGIResponse[F]] = { bytes =>
    sealed trait State

    case object State {
      case object init extends State

      case object stdout extends State

      case class header(key: Option[FastCGIParamKey], value: Option[FastCGIParamValue]) extends State
      case object body extends State
      case class skip(count: Int) extends State
    }

    def go(state: State, responseWithoutBody: FastCGIResponse[F], stream: Stream[F, Byte]): Pull[F, FastCGIResponse[F], Unit] = {
      state match {
        case State.init =>
          stream.chunkN(8, false).map(_.toArray).map({ c => println(s"c=${util.Arrays.toString(c)}") ; c }).pull.uncons1.flatMap({
            case Some((bytes, stream)) =>
              println(util.Arrays.toString(bytes))
              val version = bytes(0)
              val `type` = bytes(1)
              val id = ((bytes(2) << 8) + bytes(3)) & 0xff
              val length = ((bytes(4) << 8) + bytes(5)) & 0xff
              val padding = bytes(6)
              println(s"version=${version}, `type`=${`type`}, id=${id}, length=${length}, padding=${padding}")
              `type` match {
                case FastCGI.stdout =>
                  go(State.stdout, response, stream.flatMap({ bytes => Stream.chunk(Chunk.array(bytes)) }).take(length))
              }
            case None =>
              println("State.init.None")
              Pull.done
          })

        case State.stdout =>
          go(State.header(None, None), response, stream)

        // Parsing header key
        case State.header(Some(name), None) =>
          stream.pull.uncons1.flatMap({
            case Some((byte, stream)) =>
              byte.toChar match {
                case ':' =>
                  go(State.header(Some(name), Some("")), response, stream)
                case char =>
                  go(State.header(Some(name + char), None), response, stream)
              }
            case None =>
              println("State.header(Some, None).None")
              Pull.done
          })

        case State.header(None, None) =>
          stream.pull.uncons1.flatMap({
            case Some((byte, stream)) =>
              println(s"byte=${byte}")
              byte.toChar match {
                case '\r' =>
                  go(State.body, response, stream.drop(1))
                case char =>
                  println(s"char=${char}")
                  go(State.header(Some(s"${char}"), None), response, stream)
              }
            case None =>
              println("State.header(None, None).None")
              Pull.done
          })
        // Parsing header value
        case State.header(Some(name), Some(value)) =>
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
                  go(State.header(None, None), response.withHeader(name -> value), stream.drop(1))
                case char =>
                  go(State.header(Some(name), Some(value + char)), response, stream)
              }
            case None =>
              println("State.header(Some, Some).None")
              Pull.done
          })

        case State.body =>
          println("Yay, we are here! ")
          /*Pull.eval(stream.through(fs2.text.utf8Decode[F]).compile.toList.map(_.mkString))
            .map({ body =>
              println(s"body=[${body}]")
              response.withBody(body)
            })

          Pull.output1(response)*/

          /*Pull.output1(response.withBody(stream.map({ t => println(s"t=${t}") ; t })))*/
          Pull.output1(response.withBody(stream))

        case _ =>
          Pull.done
      }
    }

    go(State.init, FastCGIResponse.empty[F], bytes).stream
  }

}
