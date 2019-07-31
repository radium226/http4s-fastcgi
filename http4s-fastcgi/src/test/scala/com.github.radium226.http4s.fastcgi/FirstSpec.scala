package com.github.radium226.http4s.fastcgi

import java.net.Socket

import cats.effect._
import fs2._
import fs2.text
import com.github.radium226.fs2.io.socket

import scala.concurrent.duration._
import cats.implicits._
import com.github.radium226.fs2.debug.HexDump
import com.github.radium226.tools.Ansi

import scala.concurrent.ExecutionContext

class FirstSpec extends FastCGISpec {

  it should "work" in withContext(
    """#!/bin/bash
      |
      |echo "Content-type: text/html"
      |echo ""
      |echo '<html>'
      |echo '<head>'
      |echo '<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">'
      |echo '<title>Hello World</title>'
      |echo '</head>'
      |echo '<body>'
      |echo 'Hello World'
      |echo '</body>'
      |echo '</html>'
      |exit 0
    """.stripMargin)({ (scriptFilePath, fastcgiSocket) =>
    FastCGI[IO](scriptFilePath).flatMap(_.stream
      //.map({ byte => println(s"byte=${byte}") ; byte })
      .observe(hexDump(Ansi.blue))
      .through(pipe(fastcgiSocket))
      .observe(hexDump(Ansi.green))
      //.through(text.utf8Decode[IO])
      //.through(text.lines)
      //.showLinesStdOut
      .interruptAfter(5 seconds)
      .compile
      .drain
    )
  })

  def pipe(socket: Socket): Pipe[IO, Byte, Byte] = { inputBytes =>
    fs2.io.readInputStream[IO](IO(socket.getInputStream), 1, ExecutionContext.global)
      .concurrently(inputBytes.through(fs2.io.writeOutputStream[IO](IO(socket.getOutputStream), ExecutionContext.global)))
  }

  def hexDump(color: String): Pipe[IO, Byte, Unit] = { bytes =>
    bytes.through(HexDump[IO].write)
      .map({ line =>
        s"${color}${line}${Ansi.reset}"
      })
      .showLinesStdOut
  }

}
