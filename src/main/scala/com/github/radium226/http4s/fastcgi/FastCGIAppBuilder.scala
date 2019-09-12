package com.github.radium226.http4s.fastcgi

import java.nio.file.{Path, Paths}

import cats.effect._
import org.http4s._
import com.github.radium226.fastcgi._
import cats.implicits._


class FastCGIAppBuilder[F[_]](socketFilePath: Option[Path], paramWriters: List[FastCGIParamWriter[Request[F]]]) {

  def withParam(param: FastCGIParam): FastCGIAppBuilder[F] = {
    withParam({ _ => (param._1, param._2.some) })
  }

  def withParam(paramKey: FastCGIParamKey): FastCGIAppBuilder[F] = {
    withParam(paramKey -> "")
  }

  def withParam[A](paramKey: FastCGIParamKey, a: A)(implicit writer: FastCGIParamValueWriter[A]): FastCGIAppBuilder[F] = {
    withParam(paramKey, writer(a))
  }

  def withParam(paramWriter: FastCGIParamWriter[Request[F]]): FastCGIAppBuilder[F] = {
    new FastCGIAppBuilder[F](socketFilePath, paramWriters :+ paramWriter)
  }

  def withSocket(socketFilePath: Path): FastCGIAppBuilder[F] = {
    new FastCGIAppBuilder[F](Some(socketFilePath), paramWriters)
  }

  def build(implicit F: Concurrent[F], contextShift: ContextShift[F]): Resource[F, HttpApp[F]] = {
    socketFilePath
      .map({ socketFilePath =>
        Resource.liftF[F, FastCGI[F]](F.pure(FastCGI[F](socketFilePath)))
      })
      .getOrElse(FastCGI.wrapper[F])
      .flatMap({ fastCGI =>
        Resource.liftF(F.pure(HttpApp[F] { request =>
          for {
            fastCGIRequest  <- writeRequest(request)
            fastCGIResponse <- fastCGI.run(fastCGIRequest)
            response        <- readResponse(fastCGIResponse)
          } yield response
        }))
      })
  }

  def readResponse(response: FastCGIResponse[F])(implicit F: Sync[F]): F[Response[F]] = {
    val headers = Headers(response.headers.map({ case (name, value) => Header(name, value).parsed }))
    println(s"headers=${headers}")
    F.pure(Response[F]()
      .withHeaders(headers)
      .withBodyStream(response.body))
  }

  def writeRequest(request: Request[F])(implicit F: Concurrent[F]): F[FastCGIRequest[F]] = {
    FastCGIRequest[F](
      params = paramWriters.map(_.apply(request)).collect({ case (key, Some(value)) => (key, value) }).map({ t => println(s"param=${t}") ; t }),
      body = request.body
    )
  }

}

object FastCGIAppBuilder {

  def paramWriters[F[_]]: List[FastCGIParamWriter[Request[F]]] = List(
    "REQUEST_METHOD" -> _.method.name.some,
    "PATH_INFO" -> _.pathInfo.some,
    "QUERY_STRING" -> _.queryString.some,
    "CONTENT_TYPE" -> _.contentType.map(_.value),
    "CONTENT_LENGTH" -> _.contentLength.map(_.toString),
    "REQUEST_URI" -> _.uri.renderString.some,
    "REQUEST_SCHEME" -> _.uri.scheme.map(_.toString),
    { _ => "GATEWAY_INTERFACE" -> Some("CGI/1.1") },
    "SERVER_SOFTWARE" -> _.serverSoftware.product.some,
    "REMOTE_ADDR" -> _.remoteAddr,
    "REMOTE_PORT" -> _.remotePort.map(_.toString),
    "SERVER_ADDR" -> _.serverAddr.some,
    "SERVER_PORT" -> _.serverPort.toString.some
  )

  def apply[F[_]]: FastCGIAppBuilder[F] = {
    new FastCGIAppBuilder[F](None, paramWriters)
  }

}
