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
import com.google.common.io.{MoreFiles, RecursiveDeleteOption}
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
        _ <- IO(Files.write(filePath, content.getBytes(StandardCharsets.UTF_8)), StandardOpenOption.TRUNCATE_EXISTING)
        _ <- IO(Files.setPosixFilePermissions(filePath, PosixFilePermissions.fromString("r-x------")))
      } yield filePath
    })({ filePath => IO.unit /*IO(Files.deleteIfExists(filePath))*/ })
  }

  // socat -t100 -v -x UNIX-LISTEN:"%(ENV_WORK_FOLDER_PATH)s/fcgiwrap/fcgiwrap-sniffed.sock,mode=777,reuseaddr,fork" UNIX-CONNECT:"%(ENV_WORK_FOLDER_PATH)s/fcgiwrap/fcgiwrap.sock"
  private def fcgiwrapResource(folderPath: Path): Resource[IO, Socket] = {
    val socketFilePath = folderPath.resolve("fcgiwrap.sock")
    val sniffedSocketFilePath = folderPath.resolve("fcgiwrap-sniffed.sock")
    (Resource.make[IO, (Process, Process, Socket)]({
      for {
        fcgiwrapProcess <- IO(new ProcessBuilder("fcgiwrap", "-f", "-c", "2", "-s", s"unix:${socketFilePath.toString}").inheritIO().start())
        socatProcess    <- IO(new ProcessBuilder("socat", "-t100", "-v", "-x", s"UNIX-LISTEN:${sniffedSocketFilePath.toString},mode=777,reuseaddr,fork", s"UNIX-CONNECT:${socketFilePath.toString}").inheritIO().start())
        _               <- IO.sleep(1 second)
        socket          <- IO(AFUNIXSocket.newInstance())
        _               <- IO(socket.connect(new AFUNIXSocketAddress(sniffedSocketFilePath.toFile)))
      } yield (fcgiwrapProcess, socatProcess, socket)
    })({ case (fcgiwrapProcess, socatProcess, _) => pkill("socat") *> pkill("fcgiwrap") *> IO(Files.delete(socketFilePath)) }))
      .map({ case (_, _, socket) => socket })

  }

  private def pkill(pattern: String): IO[Unit] = {
    IO({
      new ProcessBuilder("pkill", pattern).start().waitFor()
    })
  }

  private def tempFolderResource: Resource[IO, Path] = {
    Resource.make[IO, Path](IO(/*Files.createTempDirectory("FastCGISpec")*/Paths.get("/tmp/FastCGISpec")))({ folderPath => /*IO(MoreFiles.deleteRecursively(folderPath, RecursiveDeleteOption.ALLOW_INSECURE))*/ IO.unit })
  }

  def withContext(content: String)(block: (Path, Socket) => IO[Unit]): Unit = {
    (for {
      tempFolder     <- tempFolderResource
      scriptFilePath <- scriptResource(tempFolder, content)
      fcgiwrapSocket <- fcgiwrapResource(tempFolder)
    } yield (scriptFilePath, fcgiwrapSocket)).use({ case (scriptFilePath, fcgiwrapSocket) =>
      block(scriptFilePath, fcgiwrapSocket)
    }).unsafeRunSync()
  }

}
