package com.github.radium226.fastcgi

import java.nio.file.Path

import cats.implicits._


object implicits extends implicits

trait implicits {

  implicit def pathWriter: FastCGIParamValueWriter[Path] = _.toString.some

}
