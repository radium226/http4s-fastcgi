package com.github.radium226

package object ansi {

  type Color = String

  object Color {

    val reset = "\u001B[0m"
    val black = "\u001B[30m"
    val red = "\u001B[31m"
    val green = "\u001B[32m"
    val yellow = "\u001B[33m"
    val blue = "\u001B[34m"
    val purple = "\u001B[35m"
    val cyan = "\u001B[36m"
    val white = "\u001B[37m"

  }

}
