package com.github.radium226.http4s.fastcgi

import java.util.{Arrays => JavaArrays}

import cats.effect._
import fs2._
import cats.implicits._
import fs2.Chunk.ByteBuffer
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

  private[fastcgi] def iterateOverChunks[F[_]](implicit F: Concurrent[F]): Pipe[F, Byte, Byte] = {
    sealed trait State
    object State {

      case class Iterate(padding: Int) extends State

      case class Stdout(length: Int, padding: Int) extends State

      case class Stderr(length: Int, padding: Int) extends State

      case object EndRequest extends State

    }

    import State._

    def go(stream: Stream[F, Byte], state: State): Pull[F, Byte, Unit] = {
      state match {
        case Iterate(padding) =>
          println(s"padding=${padding}")
          stream.drop(padding).chunkN(8, true).map(_.toArray).pull.uncons1.flatMap({
            case Some((bytes, stream)) =>
              val unsignedIntegers = bytes.map(_ & 0xff)
              val version: Int = unsignedIntegers(0)
              val `type`: Int = unsignedIntegers(1)
              val id: Int = ((unsignedIntegers(2) << 8) + unsignedIntegers(3))
              val length: Int = ((unsignedIntegers(4) << 8) + unsignedIntegers(5))
              val padding: Int = unsignedIntegers(6)

              println(s"version=${version}, `type`=${`type`}, id=${id}, length=${length}, padding=${padding}")
              `type` match {
                case FastCGI.stderr =>
                  go(stream.flatMap({ bytes => Stream.chunk(Chunk.array(bytes)) }), Stderr(length, padding))

                case FastCGI.stdout =>
                  go(stream.flatMap({ bytes => Stream.chunk(Chunk.array(bytes)) }), Stdout(length, padding))

                case FastCGI.endRequest =>
                  go(stream.flatMap({ bytes => Stream.chunk(Chunk.array(bytes)) }), EndRequest)

                case _ =>
                  println("Unknown type")
                  Pull.raiseError[F](new IllegalStateException())
              }
            case None =>
              Pull.raiseError[F](new IllegalStateException)
          })

        case EndRequest =>
          stream.chunkN(8, true).map(_.toArray).pull.uncons1.flatMap({
            case Some((endRequestHeaderBytes, stream)) =>
              val applicationStatus: Int = (endRequestHeaderBytes(0) << 24) + (endRequestHeaderBytes(1) << 16) + (endRequestHeaderBytes(2) << 8) + endRequestHeaderBytes(3)
              val processStatus: Int = endRequestHeaderBytes(4)
              println(s"applicationStatus=${applicationStatus} / processStatus=${processStatus}")
              Pull.done
            case None =>
              Pull.raiseError[F](new IllegalStateException)
          })


        case Stdout(0, padding) =>
          go(stream, Iterate(padding))

        case Stdout(length, padding) =>
          stream.pull.uncons1.flatMap({
            case Some((byte, stream)) =>
              for {
                _ <- Pull.output1(byte)
                _ <- go(stream, Stdout(length - 1, padding))
              } yield ()
            case None =>
              println("We are here! ")
              Pull.raiseError[F](new IllegalStateException())
          })

        case Stderr(0, padding) =>
          go(stream, Iterate(padding))

        case Stderr(length, padding) =>
          stream.pull.uncons1.flatMap({
            case Some((byte, stream)) =>
              for {
                _ <- Pull.eval(F.delay(Console.err.write(byte)))
                _ <- go(stream, Stderr(length - 1, padding))
              } yield ()
            case None =>
              println("We are here! ")
              Pull.raiseError[F](new IllegalStateException())
          })


      }
    }

    { bytes =>
      go(bytes, Iterate(0)).stream
    }
  }

  private[fastcgi] def pushResponseWithBodyAndResponseBody[F[_]](responseWithoutBodyBridge: Bridge[F, FastCGIResponse[F]], responseBodyBridge: Bridge[F, Byte])(implicit F: Concurrent[F]): Pipe[F, Byte, Unit] = {
    sealed trait State

    object State {

      case class Header(response: FastCGIResponse[F], name: Option[FastCGIHeaderName], value: Option[FastCGIHeaderValue]) extends State

      case object Body extends State

    }

    def go(stream: Stream[F, Byte], state: State): Pull[F, Unit, Unit] = {
      state match {
        case State.Header(response, Some(name), None) =>
          stream.pull.uncons1.flatMap({
            case Some((byte, stream)) =>
              byte.toChar match {
                case ':' =>
                  go(stream, State.Header(response, Some(name), Some("")))

                case char =>
                  go(stream, State.Header(response, Some(s"${name}${char}"), None))
              }
            case None =>
              Pull.raiseError[F](new IllegalArgumentException)
          })

        case State.Header(response, None, None) =>
          stream.pull.uncons1.flatMap({
            case Some((byte, stream)) =>
              byte.toChar match {
                case '\r' =>
                  for {
                    _ <- Pull.eval(responseWithoutBodyBridge.push1(response))
                    _ <- go(stream.drop(1), State.Body)
                  } yield ()
                case char =>
                  println(s"char=${char}")
                  go(stream, State.Header(response, Some(s"${char}"), None))
              }
            case None =>
              Pull.raiseError[F](new IllegalStateException())
          })
        // Parsing header value
        case State.Header(response, Some(name), Some(value)) =>
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
                  val header = name -> value
                  println(s"header=${header}")
                  go(stream.drop(1), State.Header(response.withHeader(header), None, None))
                case char =>
                  go(stream, State.Header(response, Some(name), Some(value :+ char)))
              }
            case None =>
              Pull.raiseError[F](new IllegalStateException())
          })

        case State.Body =>
          stream.pull.uncons1.flatMap({
            case Some((byte, stream)) =>
              for {
                _ <- Pull.eval(responseBodyBridge.push1(byte))
                _ <- go(stream, State.Body)
              } yield ()

            case None =>
              for {
                _ <- Pull.eval(responseBodyBridge.stop)
                _ <- Pull.done
              } yield ()
          })

      }
    }

    { bytes =>
      go(bytes, State.Header(FastCGIResponse.empty[F], None, None)).stream
    }
  }

  def parse[F[_]](bytes: Stream[F, Byte])(implicit F: Concurrent[F]): F[FastCGIResponse[F]] = {
    for {
      responseWithoutBodyBridge <- Bridge.start[F, FastCGIResponse[F]]
      responseBodyBridge        <- Bridge.start[F, Byte]
      _                         <- F.start(bytes.through(iterateOverChunks).through(pushResponseWithBodyAndResponseBody(responseWithoutBodyBridge, responseBodyBridge)).compile.drain)
      responseWithoutBody       <- responseWithoutBodyBridge.pull1
      responseBody               = responseBodyBridge.pull
      response                   = responseWithoutBody.withBody(responseBody)
    } yield response
  }

}
