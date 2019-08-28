package com.github.radium226

package object fastcgi {

  type ID = Long

  type FastCGIParamKey = String

  type FastCGIParamValue = String

  type FastCGIParam = (FastCGIParamKey, FastCGIParamValue)

  type FastCGIParamValueWriter[A] = A => Option[FastCGIParamValue]

  object FastCGIParam {

    def apply[A](key: FastCGIParamKey, a: A)(implicit write: FastCGIParamValueWriter[A]): Option[FastCGIParam] = {
      write(a).map({ value => (key, value) })
    }

  }

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
