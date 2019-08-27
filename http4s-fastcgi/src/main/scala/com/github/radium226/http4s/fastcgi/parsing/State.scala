package com.github.radium226.http4s.fastcgi.parsing

import com.github.radium226.http4s.fastcgi.FastCGIResponse

sealed trait State[+F[_]]

object State {

  type ParamKey = String

  type ParamValue = String

  case object Beginning extends State[Nothing]

  case class Param[F[_]](length: Int, response: FastCGIResponse[F], key: Option[ParamKey], value: Option[ParamValue]) extends State[F] {

    def appendToKey(char: Char): Param[F] = {
      copy(length = length - 1, key = key.map({ chars => Some(chars :+ char) }).getOrElse(Some(s"${char}")))
    }

    def appendToValue(char: Char): Param[F] = {
      copy(length = length - 1, value = value.map({ chars => Some(chars :+ char) }).getOrElse(Some(s"${char}")))
    }

    def empty(length: Int): Param[F] = {
      copy(length = length, response = response.withHeader(key.get, value.get), key = None, value = None)
    }

  }

  case class Stdout(length: Int) extends State[Nothing] {

    def readChar(char: Char): Stdout = {
      copy(length = length - 1)
    }

  }

  case class Body(length: Int) extends State[Nothing]

}
