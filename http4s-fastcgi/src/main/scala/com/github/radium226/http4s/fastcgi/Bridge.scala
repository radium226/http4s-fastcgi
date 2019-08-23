package com.github.radium226.http4s.fastcgi

import cats.effect.Concurrent
import fs2.{Pipe, Stream}
import fs2.concurrent.{Queue, SignallingRef}

import cats.implicits._

case class Bridge[F[_], A](queue: Queue[F, A], signal: SignallingRef[F, Boolean]) {

  def stop: F[Unit] = {
    signal.set(true)
  }

  def push1(a: A): F[Unit] = {
    queue.enqueue1(a)
  }
  def push: Pipe[F, A, Unit] = {
    queue.enqueue
  }

  def pull1: F[A] = {
    queue.dequeue1
  }

  def pull(implicit F: Concurrent[F]): Stream[F, A] = {
    queue.dequeue.interruptWhen(signal)
  }

}

object Bridge {

  def start[F[_], A](implicit F: Concurrent[F]): F[Bridge[F, A]] = {
    for {
      queue  <- Queue.bounded[F, A](1)
      signal <- SignallingRef[F, Boolean](false)
    } yield new Bridge[F, A](queue, signal)
  }

}
