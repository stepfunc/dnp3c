// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol.logging

import java.io.PrintStream

import org.fusesource.jansi.{ Ansi, AnsiConsole }

import scala.util.DynamicVariable

object Colors {

  private val width = 120

  val warn = Colors(Ansi.Color.BLUE, Ansi.Color.WHITE)
  val error = Colors(Ansi.Color.WHITE, Ansi.Color.RED)
  val text = Colors(Ansi.Color.WHITE, Ansi.Color.BLACK)
  val success = Colors(Ansi.Color.GREEN, Ansi.Color.BLACK)
  val note = Colors(Ansi.Color.BLACK, Ansi.Color.WHITE)
  val transmit = Colors(Ansi.Color.CYAN, Ansi.Color.BLACK)
  val receive = Colors(Ansi.Color.GREEN, Ansi.Color.BLACK)
  val incomplete = Colors(Ansi.Color.BLUE, Ansi.Color.WHITE)
  val plugin = Colors(Ansi.Color.YELLOW, Ansi.Color.BLACK)

  // the ANSI console, unless a test reads what's printed
  private val stream = new DynamicVariable[Option[PrintStream]](None)
  private def out: PrintStream = stream.value.getOrElse(AnsiConsole.out())

  // prints to a stream instead of the console while f runs, for tests
  private[dnp3c] def withOut[T](out: PrintStream)(f: => T): T = stream.withValue(Some(out))(f)

}

case class Colors(fg: Ansi.Color, bg: Ansi.Color) {

  private def pad(msg: String, width: Int): String = {
    if (msg.length >= width) msg else msg + (" " * (width - msg.length))
  }

  private def build(msg: String): Ansi = Ansi.ansi().bg(bg).fg(fg).a(msg).reset()

  def printLine(prefix: String, other: Colors, msg: String): Unit = {
    Colors.out.print(build(prefix)) // print the prefix
    Colors.out.println(other.build(pad(msg, Colors.width - prefix.length))) // followed by the message
  }

  def printLine(msg: String): Unit = Colors.out.println(build(pad(msg, Colors.width)))

}
