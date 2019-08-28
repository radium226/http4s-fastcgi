package com.github.radium226.http4s

import cats.effect._
import org.http4s._

trait implicits {

  implicit class PimpedHttpApp[F[_]](httpApp: HttpApp[F]) {

    def routes(implicit F: Sync[F]): HttpRoutes[F] = {
      HttpRoutes.of[F] {
        case request =>
          httpApp.run(request)
      }
    }

  }

}

object implicits extends implicits