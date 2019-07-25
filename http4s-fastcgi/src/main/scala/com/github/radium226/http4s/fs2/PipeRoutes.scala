package com.github.radium226.http4s.fs2

import cats.effect._

import fs2._
import fs2.concurrent._
import org.http4s._

import com.github.radium226.cats.effect.concurrent._

import cats.implicits._


object PipeRoutes {

  def apply[F[_]](pipe: Pipe[F, Request[F], Response[F]])(implicit F: Concurrent[F]): F[HttpRoutes[F]] = {
    for {
      lock          <- Lock[F]
      requestQueue  <- Queue.unbounded[F, Request[F]]
      responseQueue <- Queue.unbounded[F, Response[F]]
      _             <- F.start(requestQueue.dequeue.through(pipe).through(responseQueue.enqueue).compile.drain)
    } yield HttpRoutes.of[F] {
      case request =>
        lock.acquire {
          requestQueue.enqueue1(request) *> responseQueue.dequeue1
        }
    }
  }

}

