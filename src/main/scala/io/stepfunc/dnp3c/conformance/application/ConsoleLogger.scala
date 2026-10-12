// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application

import java.text.SimpleDateFormat
import java.util.Date

import io.stepfunc.dnp3c.protocol.logging.{ Colors, Details, Level, Logger }

object ConsoleLogger extends Logger {

  private val default = "     "

  override def info(msg: String): Unit = log(now(default), Colors.text, msg)

  override def warn(msg: String): Unit = log(now(default), Colors.warn, msg)

  override def error(msg: String): Unit = log(now(default), Colors.error, msg)

  override def note(msg: String, details: Iterable[Details]): Unit = {
    log(now(default), Colors.note, msg)
    details.foreach(d => log(Colors.note, d))
  }

  override def plugin(msg: String): Unit = log(now(Symbols.plugin), Colors.plugin, msg)

  override def transmit(level: Level, message: String, details: Iterable[Details]): Unit = {
    log(now(Symbols.tx(level)), txColor(level), message)
    details.foreach(d => log(Colors.text, d))
  }

  override def receive(level: Level, message: String, details: Iterable[Details]): Unit = {
    log(now(Symbols.rx(level)), rxColor(level), message)
    details.foreach(d => log(Colors.text, d))
  }

  // --------- private helpers ----------

  private def rxColor(level: Level): Colors = level match {
    case Level.One => Colors.text
    case _ => Colors.receive
  }

  private def txColor(level: Level): Colors = level match {
    case Level.One => Colors.text
    case _ => Colors.transmit
  }

  private def log(colors: Colors, details: Details): Unit = {
    details.comments.foreach(log(now(default), colors, _))
    details.details.foreach(d => log(colors, d))
  }

  private val defaultUTCDateFormat = new SimpleDateFormat("HH:mm:ss.SSS")

  private def now(symbol: String): String = defaultUTCDateFormat.format(new Date(System.currentTimeMillis())) +
    s" $symbol "

  private def log(prefix: String, color: Colors, msg: String): Unit = {
    Colors.text.printLine(prefix, color, msg)
  }
}
