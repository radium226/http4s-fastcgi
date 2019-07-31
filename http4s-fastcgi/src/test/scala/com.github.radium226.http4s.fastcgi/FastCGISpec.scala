package com.github.radium226.http4s.fastcgi

import java.lang.ProcessBuilder.Redirect
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.net.Socket
import java.nio.charset.StandardCharsets

import cats.effect._

import scala.concurrent.ExecutionContext
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util

import cats.implicits._
import com.github.radium226.fs2.debug.HexDump
import com.github.radium226.ansi._
import com.google.common.io.{MoreFiles, RecursiveDeleteOption}
import fs2.Pipe
import org.newsclub.net.unix.{AFUNIXSocket, AFUNIXSocketAddress}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._

abstract class FastCGISpec extends FlatSpec with Matchers {

  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  private def scriptResource(folderPath: Path, content: String): Resource[IO, Path] = {
    Resource.make[IO, Path]({
      for {
        filePath <- IO.pure(folderPath.resolve("cgi.sh"))
        _         = println(s"filePath=${filePath}")
        _ <- IO(Files.write(filePath, content.getBytes(StandardCharsets.UTF_8)), StandardOpenOption.TRUNCATE_EXISTING)
        _ <- IO(Files.setPosixFilePermissions(filePath, PosixFilePermissions.fromString("r-x------")))
        _        <- IO.sleep(1 second)
      } yield filePath
    })({ filePath => IO(Files.deleteIfExists(filePath)) })
  }

  private def fcgiwrapResource(folderPath: Path): Resource[IO, Path] = {
    val socketFilePath = folderPath.resolve("fcgiwrap.sock")
    (Resource.make[IO, (Process, Path)]({
      for {
        process <- IO(new ProcessBuilder("fcgiwrap", "-f", "-c", "2", "-s", s"unix:${socketFilePath.toString}").directory(folderPath.toFile).inheritIO().start())
        _       <- IO.sleep(1 second)
      } yield (process, socketFilePath)
    })({ _ => pkill("fcgiwrap") /**> IO(Files.delete(socketFilePath))*/ }))
      .map({ case (_, socketFilePath) =>
        socketFilePath
      })
  }

  private def pkill(pattern: String): IO[Unit] = {
    IO({
      new ProcessBuilder("pkill", pattern).start().waitFor()
    })
  }

  private def tempFolderResource: Resource[IO, Path] = {
    Resource.make[IO, Path](IO(Files.createTempDirectory("FastCGISpec")))({ folderPath => IO(MoreFiles.deleteRecursively(folderPath, RecursiveDeleteOption.ALLOW_INSECURE)) })
  }

  def socketResource(socketFilePath: Path): Resource[IO, Socket] = {
    Resource.make[IO, Socket](for {
      socket <- IO(AFUNIXSocket.newInstance())
      _      <- IO(socket.connect(new AFUNIXSocketAddress(socketFilePath.toFile)))
    } yield socket)({ socket =>
      IO(socket.close())
    })
  }

  def withContext(content: String)(block: (Path, Socket) => IO[Unit]): Unit = {
    (for {
      tempFolder      <- tempFolderResource
      scriptFilePath  <- scriptResource(tempFolder, content)
      socketFilePath  <- fcgiwrapResource(tempFolder)
      fcgiwrapSocket  <- socketResource(socketFilePath)
    } yield (scriptFilePath, fcgiwrapSocket)).use({ case (scriptFilePath, fcgiwrapSocket) =>
      IO(println(scriptFilePath)) *> block(scriptFilePath, fcgiwrapSocket)
    }).unsafeRunSync()
  }

  def hexDump(color: Color): Pipe[IO, Byte, Unit] = { bytes =>
    bytes.through(HexDump[IO].write)
        .map({ line =>
          s"${color}${line}${Color.reset}"
        })
        .showLinesStdOut
  }

}
