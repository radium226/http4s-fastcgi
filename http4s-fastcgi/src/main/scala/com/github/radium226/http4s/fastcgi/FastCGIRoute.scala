package com.github.radium226.http4s.fastcgi

import java.net.SocketAddress

import cats.effect._
import cats.implicits._
import java.net.Socket

import com.github.radium226.http4s.fs2.PipeRoutes
import org.http4s._
import org.http4s.Status._

import fs2._

// https://kohlschutter.github.io/junixsocket/

case class FastCGIRequest[F[_]]()

object FastCGIRequest {

  private def encodeHeader[F[_]](key: String, value: String): Stream[F, Byte] = {
    ???
  }

  def encode[F[_]]: Pipe[F, (Request[F], ID, List[Param]), Byte] = {
      ???
  }

}

case class FastCGIResponse[F[_]]()

object FastCGIResponse {

  def decode[F[_]]: Pipe[F, Byte, (Response[F], ID)] = ???

}

object FastCGIRoute {

  def apply[F[_]](socket: Socket)(pf: PartialFunction[Request[F], F[List[Param]]])(implicit F: ConcurrentEffect[F]): F[HttpRoutes[F]] = {
    PipeRoutes[F]({ requests =>
      requests.zipWithIndex
        .evalMap({ case (request, id) =>
          pf.applyOrElse[Request[F], F[List[Param]]](request, { _ => F.pure(List.empty[Param]) })
            .map({ params =>
              (request, id, params)
            })
        })
        .through(FastCGIRequest.encode[F])
        .through(com.github.radium226.fs2.io.socket.pipe[F](socket))
        .through(FastCGIResponse.decode[F])
        .map({ case (response, _) =>
          response
        })
    })
  }

}