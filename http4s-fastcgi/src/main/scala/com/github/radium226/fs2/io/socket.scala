package com.github.radium226.fs2.io

import java.net.Socket

import cats.effect._
import fs2._
import fs2.concurrent._
import cats.implicits._

object socket {

  def pipe[F[_]](socket: Socket)(implicit F: ConcurrentEffect[F]): Pipe[F, Byte, Byte] = { inputBytes =>
    Stream.eval[F, (Queue[F, Byte], Queue[F, Byte])]({
      for {
        inputByteQueue  <- Queue.bounded[F, Byte](1)
        outputByteQueue <- Queue.bounded[F, Byte](1)
      } yield (inputByteQueue, outputByteQueue)
    }).flatMap({ case (inputByteQueue, outputByteQueue) =>
      Stream.eval(for {
        inputStreamAndOutputStream <- F.delay({
          (socket.getInputStream, socket.getOutputStream)
        })

        (inputStream, outputStream) = inputStreamAndOutputStream

        _ <- F.start({
          def drainInputStream: F[Unit] = {
            F.delay(inputStream.read()).flatMap({
              case -1 =>
                //println("Done! ")
                F.unit
              case outputByte =>
                //println(s"outputByte=${outputByte}")
                outputByteQueue.enqueue1(outputByte.toByte) >> drainInputStream
            })

          }

          drainInputStream
        })

        _ <- F.start(for {
          //_ <- F.delay(println("Yoy"))
          _ <- inputByteQueue.dequeueChunk(1).evalTap({ inputByte =>
            F.delay({
              //println(s"inputByte=${inputByte}")
              outputStream.write(inputByte.toInt)
              //println("written")
              outputStream.flush()
              //println("flushed")
            })
          }).compile.drain
        } yield ())
      } yield ()).flatMap({ _ =>
        outputByteQueue
          .dequeueChunk(1)
          .concurrently(inputBytes.unchunk.through(inputByteQueue.enqueue).drain)
      })
    })
  }

}
