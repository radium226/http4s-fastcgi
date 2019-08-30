package com.github.radium226.fastcgi

import cats.effect._
import fs2._
import cats.implicits._
import com.github.radium226.fs2.concurrent.Bridge


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

  private[fastcgi] def mergeFrames[F[_]](implicit F: Concurrent[F]): Pipe[F, Byte, Byte] = {
    sealed trait State
    object State {

      case object Iterate extends State

      case class Stdout(length: Int, padding: Int) extends State

      case class Stderr(length: Int, padding: Int) extends State

      case object EndRequest extends State

    }

    import State._

    def go(stream: Stream[F, Byte], state: State): Pull[F, Byte, Unit] = {
      state match {
        case Iterate =>
          stream.chunkN(8, true).map(_.toArray).pull.uncons1.flatMap({
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

                case t =>
                  println(s"Unknown type (t=${t})")
                  Pull.raiseError[F](new IllegalStateException())
              }
            case None =>
              Pull.done
          })

        case EndRequest =>
          stream.chunkN(8, true).map(_.toArray).pull.uncons1.flatMap({
            case Some((endRequestHeaderBytes, stream)) =>
              val applicationStatus: Int = (endRequestHeaderBytes(0) << 24) + (endRequestHeaderBytes(1) << 16) + (endRequestHeaderBytes(2) << 8) + endRequestHeaderBytes(3)
              val processStatus: Int = endRequestHeaderBytes(4)
              println(s"applicationStatus=${applicationStatus} / processStatus=${processStatus}")
              go(stream.flatMap({ bytes => Stream.chunk(Chunk.array(bytes)) }), Iterate)
            case None =>
              println("Youpila")
              Pull.raiseError[F](new IllegalStateException)
          })




        case Stdout(0, padding) =>
          println(s"Yay, padding is here = ${padding}")
          go(stream.drop(padding), Iterate)

        case Stdout(length, padding) =>
          stream.pull.uncons1.flatMap({
            case Some((byte, stream)) =>
              for {
                _ <- Pull.output1(byte)
                _ <- go(stream, Stdout(length - 1, padding))
              } yield ()
            case None =>
              println("We are here! 2.")
              Pull.raiseError[F](new IllegalStateException())
          })

        case Stderr(0, padding) =>
          println(s"Yay, padding is here = ${padding}")
          go(stream.drop(padding), Iterate)

        case Stderr(length, padding) =>
          stream.pull.uncons1.flatMap({
            case Some((byte, stream)) =>
              for {
                _ <- Pull.eval(F.delay(Console.err.write(byte)))
                _ <- go(stream, Stderr(length - 1, padding))
              } yield ()
            case None =>
              println("We are here! 3.")
              Pull.raiseError[F](new IllegalStateException())
          })


      }
    }

    { bytes =>
      go(bytes, Iterate).stream
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
              println("IllegalStateException 1.")
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
                  //println(s"char=${char}")
                  go(stream, State.Header(response, Some(s"${char}"), None))
              }
            case None =>
              println("IllegalStateException 2.")
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
              println("IllegalStateException 3.")
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
      _                         <- F.start(bytes.through(mergeFrames).through(pushResponseWithBodyAndResponseBody(responseWithoutBodyBridge, responseBodyBridge)).compile.drain)
      responseWithoutBody       <- responseWithoutBodyBridge.pull1
      responseBody               = responseBodyBridge.pull
      response                   = responseWithoutBody.withBody(responseBody)
    } yield response
  }

}
