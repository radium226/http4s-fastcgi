package com.github.radium226.tools

import java.net.{ServerSocket, Socket}
import java.nio.file.{Path, Paths}

import cats._
import cats.effect._
import fs2._
import org.newsclub.net.unix._
import com.github.radium226.fs2.debug.HexDump
import fs2.concurrent._

import scala.concurrent.ExecutionContext
import cats.implicits._
import java.io._

object Ansi {

  val reset = "\u001B[0m"
  val black = "\u001B[30m"
  val red = "\u001B[31m"
  val green = "\u001B[32m"
  val yellow = "\u001B[33m"
  val blue = "\u001B[34m"
  val purple = "\u001B[35m"
  val cyan = "\u001B[36m"
  val white = "\u001B[37m"

}

object ProxyUnixSocket extends IOApp {

  def accept(serverSocket: ServerSocket): Stream[IO, Resource[IO, Socket]] = {
    Stream.eval({
      for {
        socketQueue <- Queue.unbounded[IO, Socket]
        _           <- {
          def go: IO[Unit] = {
            for {
              socket <- IO(serverSocket.accept())
              _      <- socketQueue.enqueue1(socket)
              _      <- go
            } yield ()
          }
          go.start
        }
      } yield socketQueue
    })
      .flatMap({ socketQueue =>
        socketQueue.dequeue
      })
      .map({ socket =>
        Resource.make(IO.pure(socket))({ socket => IO(println("Closing socket")) *> IO(socket.close()) })
      })
  }

  def pump(inputStream: IO[InputStream], outputStream: IO[OutputStream], color: String): Stream[IO, Unit] = {
    fs2.io.readInputStream(inputStream, 1, ExecutionContext.global).observe(hexDump(color)).through(fs2.io.writeOutputStream(outputStream, ExecutionContext.global))
  }

  def pump(oneSocket: Socket, otherSocket: Socket): IO[Unit] = {
    pump(IO(oneSocket.getInputStream), IO(otherSocket.getOutputStream), Ansi.blue).concurrently(pump(IO(otherSocket.getInputStream), IO(oneSocket.getOutputStream), Ansi.green))
      .compile
      .drain
  }

  def hexDump(color: String): Pipe[IO, Byte, Unit] = { bytes =>
    bytes.through(HexDump[IO].write)
      .map({ line =>
        s"${color}${line}${Ansi.reset}"
      })
      .showLinesStdOut
  }

  def connect(socketFilePath: Path): Stream[IO, Resource[IO, Socket]] = {
    Stream.eval(IO(Resource.make[IO, Socket]({
      for {
        socket <- IO(AFUNIXSocket.newInstance())
        _      <- IO(socket.connect(new AFUNIXSocketAddress(socketFilePath.toFile)))
      } yield socket
    })({ socket => IO(println("Closing socket")) *> IO(socket.close()) })))
  }

  override def run(arguments: List[String]): IO[ExitCode] = {
    val socketFilePath = Paths.get("/tmp/reverse-engineering/work/fcgiwrap/fcgiwrap.sock")
    val proxySocketFilePath = Paths.get("/tmp/reverse-engineering/work/fcgiwrap/proxy-fcgiwrap.sock")

    (for {
      proxyServerSocket   <- Stream.eval[IO, ServerSocket](IO(AFUNIXServerSocket.newInstance()))
      _                   <- Stream.eval[IO, Unit](IO(proxyServerSocket.bind(new AFUNIXSocketAddress(proxySocketFilePath.toFile))))
      proxySocketResource <- accept(proxyServerSocket)
      socketResource      <- connect(socketFilePath)
      _                   <- Stream.eval({
        (for {
          socket      <- socketResource
          proxySocket <- proxySocketResource
        } yield (socket, proxySocket))
          .use({ case (socket, proxySocket) =>
            pump(socket, proxySocket)
          })
      })
    } yield ())
      .compile
      .drain
      .as(ExitCode.Success)
  }

}
