package com.github.radium226.http4s

package object fastcgi {

  type ID = Long

  type FastCGIParamKey = String

  type FastCGIParamValue = String

  type FastCGIParam = (FastCGIParamKey, FastCGIParamValue)

  type FastCGIParams = List[FastCGIParam]

  object FastCGIParams {

    def empty: FastCGIParams = List.empty

  }

  type FastCGIHeaderName = String

  type FastCGIHeaderValue = String

  type FastCGIHeader = (FastCGIHeaderName, FastCGIHeaderValue)

  type FastCGIHeaders = List[FastCGIHeader]

  object FastCGIHeaders {

    def empty: FastCGIHeaders = List.empty

  }

  type FastCGIBody[F[_]] = _root_.fs2.Stream[F, Byte]

  object FastCGIBody {

    def empty[F[_]]: FastCGIBody[F] = _root_.fs2.Stream.empty

  }

  type FastCGISocket = java.net.Socket

}
