package com.github.radium226.http4s.fastcgi

import java.net.Socket
import java.nio.file._

import cats.effect._
import fs2._
import cats.implicits._
import com.github.radium226.ansi.Color
import com.github.radium226.fs2.debug.HexDump
import org.newsclub.net.unix.{AFUNIXSocket, AFUNIXSocketAddress}

case class FastCGI[F[_]](socketFilePath: Path) {

  def connect(implicit F: Sync[F]): Resource[F, FastCGISocket] = {
    Resource.make[F, Socket](for {
      socket <- F.delay(AFUNIXSocket.newInstance())
      _      <- F.delay(socket.connect(new AFUNIXSocketAddress(socketFilePath.toFile)))
    } yield socket)({ socket =>
      F.delay(socket.close())
    })
  }

  def run(request: FastCGIRequest[F])(implicit F: Concurrent[F], contextShift: ContextShift[F]): F[FastCGIResponse[F]] = {
    val outputBytes = Stream.resource(connect)
      .flatMap({ socket =>
        request
          .stream
          .observe(FastCGI.hexDump[F](Color.blue))
          .through(FastCGISocket.pipe[F](socket))
          .observe(FastCGI.hexDump[F](Color.green))
      })
    FastCGIResponse.parse(outputBytes)
  }

}

object FastCGI {

  val beginRequest: Byte = 1
  val endRequest: Byte = 3
  val params: Byte = 4
  val stdin: Byte = 5
  val stdout: Byte = 6
  val stderr: Byte = 7
  val responder: Byte = 1
  val version = 1
  val keepConnected: Byte = 1
  val requestComplete: Byte = 0

  def hexDump[F[_]](color: Color)(implicit F: Concurrent[F]): Pipe[F, Byte, Unit] = { bytes =>
    bytes.through(HexDump[F].write)
      .map({ line =>
        s"${color}${line}${Color.reset}"
      })
      .showLinesStdOut
  }

}
