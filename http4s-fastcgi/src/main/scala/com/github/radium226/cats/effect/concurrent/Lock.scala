package com.github.radium226.cats.effect.concurrent

import cats._
import cats.effect._
import cats.effect.concurrent._

import cats.implicits._


object Lock {

  def apply[F[_]](implicit F: Concurrent[F]): F[Lock[F]] = {
    MVar.of[F, Unit](()).map(new Lock[F](_))
  }

}

case class Lock[F[_]](mVar: MVar[F, Unit]) {

  def acquire[A](f: => F[A])(implicit F: Sync[F]): F[A] = {
    for {
      _ <- mVar.take
      a <- f
      _ <- mVar.put(())
    } yield a
  }

}
