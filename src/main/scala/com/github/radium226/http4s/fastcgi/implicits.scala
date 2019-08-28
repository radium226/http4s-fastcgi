package com.github.radium226.http4s.fastcgi

import java.nio.file.Path

import cats.implicits._
import com.github.radium226.fastcgi.FastCGIParamValueWriter


object implicits extends implicits

trait implicits {

  implicit def pathWriter: FastCGIParamValueWriter[Path] = _.toString.some

}
