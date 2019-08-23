package com.github.radium226.http4s.fastcgi

import java.nio.file.{Path, Paths}

import cats.effect._
import cats.effect.concurrent.MVar
import fs2._
import cats.implicits._


object FastCGIRequest {

  def apply[F[_]](params: FastCGIParams = List.empty, body: Stream[F, Byte] = Stream.empty)(implicit F: Concurrent[F]): F[FastCGIRequest[F]] = {
    MVar.of[F, Short](1).map(new FastCGIRequest[F](params, body, _))
  }

}

class FastCGIRequest[F[_]](params: FastCGIParams, body: Stream[F, Byte], requestIDMVar: MVar[F, Short]) {

  import scala.language.implicitConversions

  implicit def intToByte(i: Int): Byte = i.toByte

  implicit def shortToByte(s: Short): Byte = s.toByte

  def record(t: Int, l: Int): Stream[F, Byte] = {
    for {
      i <- Stream.eval[F, Short](requestIDMVar.read)
      b <- Stream[F, Byte](List[Byte](FastCGI.version, t, i >> 8, i, l >> 8, l, 0, 0): _*)
    } yield b
  }

  def length(l: Int): Stream[F, Byte] = {
    Stream[F, Byte]((if (l < 0x80) List[Byte](l) else List[Byte](0x80 | l >> 24, l >> 16, l >> 8, l)): _*)
  }

  def content(s: String): Stream[F, Byte] = {
    Stream[F, Byte](s.getBytes(): _*)
  }

  def param(p: FastCGIParam): Stream[F, Byte] = {
    val (k, v) = p
    println(s"k=${k} / v=${v}")
    param(k, v)
  }

  def param(k: String, v: String): Stream[F, Byte] = {
    val kl = k.length
    val vl = v.length
    val l = (kl + vl) + (if (kl < 0x80) 1 else 4) + (if (vl < 0x80) 1 else 4)

    record(FastCGI.params, l) ++
    length(kl) ++
    length(vl) ++
    content(k) ++
    content(v)
  }

  def role(r: Int): Stream[F, Byte] = {
    Stream[F, Byte](List[Byte](r >> 8, r): _*)
  }

  def keepConnected(k: Boolean): Stream[F, Byte] = {
    Stream[F, Byte](if (k) FastCGI.keepConnected else 0)
  }

  def stream: Stream[F, Byte] = {
    record(FastCGI.beginRequest, 8) ++
    role(FastCGI.responder) ++
    keepConnected(false) ++
    Stream[F, Byte](0).repeatN(5) ++
    params.foldLeft[Stream[F, Byte]](Stream.empty) { (s, p) => s ++ param(p) } ++
    record(FastCGI.params, 0) ++
    body.chunks.flatMap({ c => record(FastCGI.stdin, c.size) ++ Stream.chunk(c) }) ++ record(FastCGI.stdin, 0)
  }

}
