package com.github.radium226.fs2.debug

import _root_.org.apache.commons.io.{ HexDump => HD}

import java.io.ByteArrayOutputStream
import java.nio.file.Paths

import cats.effect._
import cats.effect.concurrent._
import cats.implicits._
import fs2._

import scala.concurrent.ExecutionContext

case class HexDump2[F[_]]() {

  def write(implicit F: Concurrent[F]): Pipe[F, Byte, String] = { bytes =>
    for {
      offsetRef <- Stream.eval[F, Ref[F, Long]](Ref[F].of(0))
      byteChunk <- bytes.chunkN(16, true)
      line      <- Stream.eval(for {
        offset <- offsetRef.get
        line   <- F.delay({
          val byteArrayOutputStream = new ByteArrayOutputStream()
          HD.dump(byteChunk.toArray, offset, byteArrayOutputStream, 0)
          new String(byteArrayOutputStream.toByteArray).trim
        })
        _      <- offsetRef.set(offset + 1)
      } yield line)
    } yield line
  }

  def read(implicit F: Concurrent[F]): Pipe[F, String, Byte] = { lines =>
    val pattern = s"^[0-9]{8}${" ([0-9A-F]{2})?" * 16} .*$$".r
    lines
      .flatMap({ line =>
        pattern.findFirstMatchIn(line) match {
          case Some(m) =>
            Stream[F, Byte]((1 to m.groupCount)
              .map({ groupIndex => Option(m.group(groupIndex)) })
              .collect({ case Some(group) =>
                group
              })
              .map({ group =>
                ((Character.digit(group.charAt(0), 16) << 4) + Character.digit(group.charAt(1), 16)).toByte
              })
              .toArray: _*)
          case None =>
            Stream.raiseError[F](new Exception(s"Can't parse ${line}! "))
        }
      })
  }

}
