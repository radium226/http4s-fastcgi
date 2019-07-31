package com.github.radium226.fs2.io

import cats.effect._

import fs2._
import fs2.concurrent._

import cats.implicits._


object process {

  def pipe[F[_]](command: List[String])(implicit F: ConcurrentEffect[F]): Pipe[F, Byte, Byte] = { inputBytes =>
    Stream.eval[F, (Queue[F, Byte], Queue[F, Byte])]({
      for {
        inputByteQueue  <- Queue.bounded[F, Byte](1)
        outputByteQueue <- Queue.bounded[F, Byte](1)
      } yield (inputByteQueue, outputByteQueue)
    }).flatMap({ case (inputByteQueue, outputByteQueue) =>
      Stream.eval(for {
        process <- F.delay({
          //println(s"command=${command}")
          val processBuilder = new ProcessBuilder()
              .command(command: _*)
              .redirectInput(ProcessBuilder.Redirect.PIPE)
              .redirectError(ProcessBuilder.Redirect.INHERIT)
              .redirectOutput(ProcessBuilder.Redirect.PIPE)
          val process = processBuilder.start()
          //println(s"process=${process}")
          process
        })

        inputStream = process.getInputStream
        outputStream = process.getOutputStream

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
