package com.github.radium226.http4s.fastcgi

import cats.effect._
import fs2._
import fs2.text

import com.github.radium226.fs2.io.socket

import scala.concurrent.duration._

import cats.implicits._

class FirstSpec extends FastCGISpec {

  it should "work" in withContext(
    """
      |#!/bin/bash
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
      .observe(Debug.hexDump[IO])
      .through(socket.pipe(fastcgiSocket))
      .through(text.utf8Decode[IO])
      .showLinesStdOut
      .interruptAfter(5 seconds)
      .compile
      .drain
    )
  })

}
