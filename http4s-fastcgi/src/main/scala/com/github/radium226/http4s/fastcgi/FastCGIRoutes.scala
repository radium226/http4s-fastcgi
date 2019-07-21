package com.github.radium226.http4s.fastcgi

import java.net.SocketAddress

import cats.effect._
import cats.implicits._
import fs2.io.tcp.Socket
import org.http4s._
import org.http4s.Status._

// https://kohlschutter.github.io/junixsocket/
object FastCGIRoutes {

  def using[F[_]](socketAddress: SocketAddress)(pf: PartialFunction[Request[F], F[List[Param]]])(implicit F: Concurrent[F]): HttpRoutes[F] = {
    HttpRoutes.of[F]({
      pf.andThen(_.map({ params =>
        Response[F](Ok)
      }))
    })
  }

}
