package com.github.radium226.http4s.fastcgi

import cats.effect._
import com.github.radium226.fastcgi._


class EndToEndParamSpec extends FastCGISpec {

  val params: FastCGIParams = List(
    "BIM_BAM_BOUM" -> "Pipou",
    "HELLO" -> "World"
  )

  "FastCGIRequest" should "be able to emit params" in withFCGIWrap(
    s"""#!/bin/bash
      |
      |echo "Content-type: text/html"
      |echo
      |env | grep -E '^(${params.map({ case (key, _) => key}).mkString("|")})='
      |exit 0
    """.stripMargin)({ case (scriptFilePath, fastCGISocketFilePath) =>
    val fastCGI = FastCGI[IO](fastCGISocketFilePath)
    for {
      fastCGIRequest  <- FastCGIRequest[IO](params :+ "SCRIPT_FILENAME" -> scriptFilePath.toString)
      fastCGIResponse <- fastCGI.run(fastCGIRequest)
      body            <- fastCGIResponse.body.through(fs2.text.utf8Decode[IO]).compile.fold("")(_ + _)
      _                = println(s"-->${body}<--")
    } yield ()
  })

}
