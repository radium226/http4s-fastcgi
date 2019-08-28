package com.github.radium226.http4s.fastcgi

import cats.effect.IO
import com.github.radium226.fastcgi.{FastCGI, FastCGIRequest}

class EndToEndSpec extends FastCGISpec {

  it should "work" in withFCGIWrap(
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
    """.stripMargin)({ case (scriptFilePath, fastCGISocketFilePath) =>
    val fastCGI = FastCGI[IO](fastCGISocketFilePath)
    for {
      fastCGIRequest  <- FastCGIRequest[IO](List("SCRIPT_FILENAME" -> scriptFilePath.toString))
      fastCGIResponse <- fastCGI.run(fastCGIRequest)
      body            <- fastCGIResponse.body.through(fs2.text.utf8Decode[IO]).compile.fold("")(_ + _)
      _                = println(s"-->${body}<--")
    } yield ()
  })

}
