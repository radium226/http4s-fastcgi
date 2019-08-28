package com.github.radium226.fastcgi

import cats.effect._
import fs2._

import scala.concurrent._


object FastCGISocket {

  def pipe[F[_]](socket: FastCGISocket)(implicit F: Concurrent[F], contextShift: ContextShift[F]): Pipe[F, Byte, Byte] = { inputBytes =>
    fs2.io.readInputStream[F](F.delay(socket.getInputStream), 1, ExecutionContext.global)
      .concurrently(inputBytes.through(fs2.io.writeOutputStream[F](F.delay(socket.getOutputStream), ExecutionContext.global)))
  }

}
