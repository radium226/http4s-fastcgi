package com.github.radium226.scopt

import java.nio.file.{Path, Paths}

import scopt.Read, scopt.Read.reads


trait implicits {

  implicit def readPath: Read[Path] = reads(Paths.get(_))

}

object implicits extends implicits