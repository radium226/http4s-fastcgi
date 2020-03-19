package com.github.radium226.http4s.fastcgi

import java.lang.{Process => JavaProcess, ProcessBuilder => JavaProcessBuilder}
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
import com.github.radium226.fs2.debug.HexDump2
import com.github.radium226.ansi._
import com.google.common.io.{MoreFiles, RecursiveDeleteOption}
import fs2.Pipe
import org.newsclub.net.unix.{AFUNIXSocket, AFUNIXSocketAddress}
import org.scalatest._

import scala.concurrent.duration._
import sys.process._

abstract class FastCGISpec extends FlatSpec with Matchers {

  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  val gitDebug = Seq(
    "GIT_SSH",
    "GIT_TRACE",
    "GIT_FLUSH",
    "GIT_TRACE_PACK_ACCESS",
    "GIT_TRACE_PACKET",
    "GIT_TRACE_PERFORMANCE",
    "GIT_TRACE_SETUP",
    "GIT_CURL_VERBOSE",
    "GIT_REFLOG_ACTION",
    "GIT_NAMESPACE",
    "GIT_DIFF_OPTS",
    "GIT_EXTERNAL_DIFF",
    "GIT_DIFF_PATH_COUNTER",
    "GIT_DIFF_PATH_TOTAL",
    "GIT_MERGE_VERBOSITY"
  )

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
    (Resource.make[IO, (JavaProcess, Path)]({
      for {
        process <- IO(new JavaProcessBuilder("fcgiwrap", "-f", "-c", "2", "-s", s"unix:${socketFilePath.toString}").directory(folderPath.toFile).inheritIO().start())
        _       <- IO.sleep(1 second)
      } yield (process, socketFilePath)
    })({ _ => pkill("fcgiwrap") /**> IO(Files.delete(socketFilePath))*/ }))
      .map({ case (_, socketFilePath) =>
        socketFilePath
      })
  }

  private def pkill(pattern: String): IO[Unit] = {
    IO({
      new JavaProcessBuilder("pkill", pattern).start().waitFor()
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

  def withFCGIWrap(content: String)(block: (Path, Path) => IO[Unit]): Unit = {
    (for {
      tempFolder      <- tempFolderResource
      scriptFilePath  <- scriptResource(tempFolder, content)
      socketFilePath  <- fcgiwrapResource(tempFolder)
    } yield (scriptFilePath, socketFilePath)).use({ case (scriptFilePath, socketFilePath) =>
      IO(println(scriptFilePath)) *> block(scriptFilePath, socketFilePath)
    }).unsafeRunSync()
  }

  def hexDump(color: Color): Pipe[IO, Byte, Unit] = { bytes =>
    bytes.through(HexDump2[IO].write)
        .map({ line =>
          s"${color}${line}${Color.reset}"
        })
        .showLinesStdOut
  }

  def initRepo(): IO[Path] = {
    for {
      remoteRepoFolderPath <- IO(Files.createTempDirectory("git-remote-repo"))
      _                    <- IO(Seq("git", "init", "--bare", remoteRepoFolderPath.toString, "--shared") !)
      _                    <- IO(Seq("git", "config", "--file", remoteRepoFolderPath.resolve("config").toString, "http.receivepack", "true") !)
    } yield remoteRepoFolderPath
  }

  def cloneRepo(host: String, port: Int): IO[Path] = {
    for {
      localRepoFolderPath <- IO(Files.createTempDirectory("git-local-repo"))
      _                   <- IO(Seq("env", "--") ++ gitDebug.map({ v => s"${v}=1" }) ++ Seq("git", "clone", s"http://${host}:${port}/", localRepoFolderPath.toString) !)
    } yield localRepoFolderPath
  }

}
