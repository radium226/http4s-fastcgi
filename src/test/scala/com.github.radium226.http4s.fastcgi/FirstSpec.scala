package com.github.radium226.http4s.fastcgi

import java.net.Socket

import cats.effect._
import fs2._
import fs2.text

import com.github.radium226.ansi.Color

import scala.concurrent.duration._
import cats.implicits._
import com.github.radium226.fastcgi.FastCGIRequest
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
    FastCGIRequest[IO](List("SCRIPT_FILENAME" -> scriptFilePath.toString)).flatMap(_.stream
        //.map({ byte => println(s"byte=${byte}") ; byte })
        .observe(hexDump(Color.blue))
        .through(pipe(fastcgiSocket))
        .observe(hexDump(Color.green))
        //.through(text.utf8Decode[IO])
        //.through(text.lines)
        //.showLinesStdOut
        .interruptAfter(5 seconds)
        .compile
        .drain
    )
  })

  it should "also work" in withContext(
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
      |echo "$( cat )"
      |echo '</body>'
      |echo '</html>'
      |exit 0
    """.stripMargin)({ (scriptFilePath, fastcgiSocket) =>
    FastCGIRequest[IO](List("SCRIPT_FILENAME" -> scriptFilePath.toString), body = Stream[IO, Byte]("Kikoo".getBytes: _*)).flatMap(_.stream
        //.map({ byte => println(s"byte=${byte}") ; byte })
        .observe(hexDump(Color.blue))
        .through(pipe(fastcgiSocket))
        .observe(hexDump(Color.green))
        //.through(text.utf8Decode[IO])
        //.through(text.lines)
        //.showLinesStdOut
        .interruptAfter(5 seconds)
        .compile
        .drain
    )
  })

  def pipe(socket: Socket): Pipe[IO, Byte, Byte] = { inputBytes =>
    Stream.resource[IO, Blocker](Blocker[IO]).flatMap({ blocker =>
      fs2.io.readInputStream[IO](IO(socket.getInputStream), 1, blocker)
        .concurrently(inputBytes.through(fs2.io.writeOutputStream[IO](IO(socket.getOutputStream), blocker)))
    })
  }

}
