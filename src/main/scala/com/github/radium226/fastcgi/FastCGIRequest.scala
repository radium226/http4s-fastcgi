package com.github.radium226.fastcgi

import java.io.{ByteArrayOutputStream, DataOutputStream}
import java.nio.file.Path

import cats.effect._
import cats.effect.concurrent._
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

  def withBody(body: FastCGIBody[F]): FastCGIRequest[F] = {
    new FastCGIRequest[F](params, body, requestIDMVar)
  }

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

  def param(p: FastCGIParam): Array[Byte] = {
    val (k, v) = p
    println(s"k=${k} / v=${v}")
    param(k, v)
  }

  def param(k: String, v: String): Array[Byte] = {
    val byteArrayOutputStream = new ByteArrayOutputStream()
    val dataOutputStream = new DataOutputStream(byteArrayOutputStream)

    val kl = k.length
    if (kl < 128) dataOutputStream.writeByte(kl)
    else dataOutputStream.writeLong(kl | 0x80000000)

    val vl = v.length
    if (vl < 128) dataOutputStream.writeByte(vl)
    else dataOutputStream.writeLong(vl | 0x80000000)

    dataOutputStream.writeBytes(k)
    dataOutputStream.writeBytes(v)

    val byteArray = byteArrayOutputStream.toByteArray

    byteArray
  }

  def role(r: Int): Stream[F, Byte] = {
    Stream[F, Byte](List[Byte](r >> 8, r): _*)
  }

  def keepConnected(k: Boolean): Stream[F, Byte] = {
    Stream[F, Byte](if (k) FastCGI.keepConnected else 0)
  }

  def stream: Stream[F, Byte] = {
    val paramBytes = params.foldLeft[Array[Byte]](Array.empty) { (s, p) => s ++ param(p) }
    val paramStream = Stream[F, Byte](paramBytes:_ *)
    val paramByteCount = paramBytes.length

    record(FastCGI.beginRequest, 8) ++
    role(FastCGI.responder) ++
    keepConnected(false) ++
    Stream[F, Byte](0).repeatN(5) ++
    record(FastCGI.params, paramByteCount) ++
    paramStream ++
    record(FastCGI.params, 0) ++
    body./*map({ u => println(u) ; u }).*/chunks.flatMap({ c => record(FastCGI.stdin, c.size) ++ Stream.chunk(c) }) ++ record(FastCGI.stdin, 0)
  }

}
