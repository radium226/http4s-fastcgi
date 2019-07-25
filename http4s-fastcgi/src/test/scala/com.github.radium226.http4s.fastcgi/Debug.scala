package com.github.radium226.http4s.fastcgi

import java.nio.ByteBuffer

import cats.effect._
import cats.effect.concurrent._
import fs2._

import cats.implicits._

import scala.concurrent.duration._

import org.apache.commons.io.HexDump

object Debug {



  def hexDump[F[_]](implicit F: Concurrent[F], timer: Timer[F]): Pipe[F, Byte, Unit] = { bytes =>
    for {
      bytesRef <- Stream.eval(Ref.of[F, List[Byte]](List.empty[Byte]))
      _        <- Stream.eval(F.start({
        def doHexDump: F[Unit] = {
          for {
            bytes <- bytesRef.get
            _     <- F.delay(System.out.println("/* ----- HEX DUMP ----- */"))
            _     <- if (bytes.size > 0) F.delay(HexDump.dump(bytes.toArray, 0l, System.out, 0)) else F.unit
            _     <- F.delay(System.out.println("/* -------------------- */"))
            _     <- F.delay(System.out.println())
            _     <- F.delay(System.out.flush())
            _     <- timer.sleep(1 second)
            _     <- doHexDump
          } yield ()
        }

        doHexDump
      }))

      byte     <- bytes
      oldBytes <- Stream.eval(bytesRef.get)
      _        <- Stream.eval(bytesRef.set(oldBytes :+ byte))
    } yield ()
  }

}
