package com.github.radium226.http4s.fastcgi

import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util

import cats.effect._
import cats.effect.concurrent.MVar
import fs2._
import cats.implicits._
import com.github.radium226.example.FastCGI
import com.github.radium226.example.ShowHex.writeHeader

object FastCGI {

  val BeginRequest = 1
  val EndRequest = 3
  val Params = 4
  val StdIn = 5
  val StdOut = 6
  val StdErr = 7
  val Responder = 1
  val Version = 1
  val KeepConnected = 1
  val RequestComplete = 0

  def apply[F[_]](scriptFilePath: Path)(implicit F: Concurrent[F]): F[FastCGI[F]] = {
    MVar.of[F, Short](1).map(new FastCGI[F](scriptFilePath, _))
  }

}

// https://github.com/nginx/nginx/blob/1305b8414d22610b0820f6df5841418bf98fc370/src/http/modules/ngx_http_fastcgi_module.c

class FastCGI[F[_]](scriptFilePath: Path, requestIDMVar: MVar[F, Short]) {

  def record(`type`: Int, length: Int): Stream[F, Byte] = {
    (for {
      requestID <- Stream.eval[F, Short](requestIDMVar.read)
      //_         <- Stream.eval[F, Unit](requestIDMVar.put((requestID + 1).toShort))
      byte      <- Stream[F, Byte]({
        val pad = 0
        val ws = new ByteArrayOutputStream()
        ws.write(FastCGI.Version)
        ws.write(`type`)
        ws.write(requestID >> 8)
        ws.write(requestID)
        ws.write(length >> 8)
        ws.write(length)
        ws.write(pad)
        ws.write(0)
        val a = ws.toByteArray
        //println(s"a=${util.Arrays.toString(a)}")
        a
      }: _*)
    } yield byte)/*.map({ t => println(s"t=${t}") ; t })*/
  }

  def param(key: String, value: String): Stream[F, Byte] = {
    val keyLen = key.length
    val valLen = value.length
    var len = keyLen + valLen
    if (keyLen < 0x80) len += 1
    else len += 4
    if (valLen < 0x80) len += 1
    else len += 4

    record(FastCGI.Params, len) ++ Stream[F, Byte]({
      val ws = new ByteArrayOutputStream()
      if (keyLen < 0x80) ws.write(keyLen)
      else {
        ws.write(0x80 | keyLen >> 24)
        ws.write(keyLen >> 16)
        ws.write(keyLen >> 8)
        ws.write(keyLen)
      }
      if (valLen < 0x80) ws.write(valLen)
      else {
        ws.write(0x80 | valLen >> 24)
        ws.write(valLen >> 16)
        ws.write(valLen >> 8)
        ws.write(valLen)
      }
      ws.write(key.getBytes)
      ws.write(value.getBytes)
      val a = ws.toByteArray
      //println(util.Arrays.toString(a))
      a
    }: _*)
  }

  def stream: Stream[F, Byte] = {
    record(FastCGI.BeginRequest, 8) ++ Stream[F, Byte]({
      val ws = new ByteArrayOutputStream()
      val keepAlive = false
      val role = FastCGI.Responder
      ws.write(role >> 8)
      ws.write(role)
      ws.write(if (keepAlive) FastCGI.KeepConnected else 0) // flags
      for (i <- 0 to 4) {
        ws.write(0)
      }
      ws.toByteArray
    }: _*) ++ param("SCRIPT_FILENAME", scriptFilePath.toString) ++ record(FastCGI.Params, 0)


  }

}
