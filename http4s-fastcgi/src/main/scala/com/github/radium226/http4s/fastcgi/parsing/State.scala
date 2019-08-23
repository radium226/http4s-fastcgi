package com.github.radium226.http4s.fastcgi.parsing

sealed trait State

object State {

  type ParamKey = String

  type ParamValue = String

  case class Param(length: Int, key: Option[ParamKey], value: Option[ParamValue]) extends State {

    def appendToKey(char: Char): Param = {
      copy(length = length - 1, key = key.map({ chars => Some(chars :+ char) }).getOrElse(Some(s"${char}")))
    }

    def appendToValue(char: Char): Param = {
      copy(length = length - 1, value = value.map({ chars => Some(chars :+ char) }).getOrElse(Some(s"${char}")))
    }

  }

  object Param {

    def empty(length: Int): Param = Param(length, None, None)

  }

}
