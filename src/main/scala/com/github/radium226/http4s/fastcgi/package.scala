package com.github.radium226.http4s

import com.github.radium226.fastcgi.{FastCGIHeader, FastCGIParamKey, FastCGIParamValue}

package object fastcgi {

  type FastCGIHeaderReader[A] = PartialFunction[FastCGIHeader, A => A]

  type FastCGIParamWriter[A] = A => (FastCGIParamKey, Option[FastCGIParamValue])

  object FastCGIParamWriter {

    def apply[A](f: FastCGIParamWriter[A]): FastCGIParamWriter[A] = f

  }

}
