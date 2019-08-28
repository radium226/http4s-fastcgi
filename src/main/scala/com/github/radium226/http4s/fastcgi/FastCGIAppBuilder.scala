package com.github.radium226.http4s.fastcgi

import java.nio.file.{Path, Paths}

import cats.effect._
import org.http4s._
import com.github.radium226.fastcgi._
import cats.implicits._


class FastCGIAppBuilder[F[_]](socketFilePath: Path, params: FastCGIParams) {

  def withParam(param: FastCGIParam): FastCGIAppBuilder[F] = {
    new FastCGIAppBuilder[F](socketFilePath, params :+ param)
  }

  def withParam[A](paramKey: FastCGIParamKey, a: A)(implicit writer: FastCGIParamValueWriter[A]): FastCGIAppBuilder[F] = {
    new FastCGIAppBuilder[F](socketFilePath, FastCGIParam(paramKey, a).map(params :+ _).getOrElse(params))
  }

  def withSocket(socketFilePath: Path): FastCGIAppBuilder[F] = {
    new FastCGIAppBuilder[F](socketFilePath, params)
  }

  def build(implicit F: Concurrent[F], contextShift: ContextShift[F]): HttpApp[F] = {
    val fastCGI = FastCGI[F](socketFilePath)
    HttpApp { request =>
      for {
        fastCGIRequest  <- writeRequest(request)
        fastCGIResponse <- fastCGI.run(fastCGIRequest)
        response        <- readResponse(fastCGIResponse)
      } yield response
    }
  }

  def readResponse(response: FastCGIResponse[F])(implicit F: Sync[F]): F[Response[F]] = {
    F.pure(Response[F](
      headers = Headers(response.headers.map({ case (name, value) => Header(name, value).parsed })),
      body = response.body
    ))
  }

  def writeRequest(request: Request[F])(implicit F: Concurrent[F]): F[FastCGIRequest[F]] = {
    FastCGIRequest[F](
      params = params ++ FastCGIAppBuilder.paramWriters[F].map(_.apply(request)).collect({ case (key, Some(value)) => (key, value) }),
      body = request.body
    )
  }

}

object FastCGIAppBuilder {

  def paramWriters[F[_]]: List[FastCGIParamWriter[Request[F]]] = List(
    "REQUEST_METHOD" -> _.method.name.some,
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
    "SERVER_PORT" -> _.serverPort.toString.some,
    "PATH_INFO" -> _.pathInfo.some
    // SERVER_NAME
  )

  def apply[F[_]]: FastCGIAppBuilder[F] = {
    new FastCGIAppBuilder[F](Paths.get("/run/fcgiwrap.sock"), FastCGIParams.empty)
  }

}
