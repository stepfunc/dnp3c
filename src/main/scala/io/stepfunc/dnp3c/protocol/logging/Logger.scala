// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol.logging

sealed trait Level {}

object Level {
  object One extends Level
  object Two extends Level
  object Three extends Level
  object Four extends Level
}

case class Details(comments: List[String], details: Iterable[Details])

object Logger {
  def empty: Logger = new MultiLogger(Nil)
  def aggregate(loggers: Logger*): Logger = new MultiLogger(loggers.toList)
}

trait Logger {

  def info(lines: String): Unit

  def warn(lines: String): Unit

  def error(lines: String): Unit

  def note(message: String, details: Iterable[Details] = Nil): Unit

  def plugin(message: String): Unit

  def transmit(level: Level, message: String, details: Iterable[Details] = Nil): Unit

  def receive(level: Level, message: String, details: Iterable[Details] = Nil): Unit
}

private class MultiLogger(loggers: List[Logger]) extends Logger {

  def info(lines: String): Unit = {
    loggers.foreach(l => l.info(lines))
  }

  def warn(lines: String): Unit = {
    loggers.foreach(l => l.warn(lines))
  }

  def error(lines: String): Unit = {
    loggers.foreach(l => l.error(lines))
  }

  def note(message: String, details: Iterable[Details] = Nil): Unit = {
    loggers.foreach(l => l.note(message, details))
  }

  def plugin(message: String): Unit = {
    loggers.foreach(l => l.plugin(message))
  }

  def transmit(level: Level, message: String, details: Iterable[Details] = Nil): Unit = {
    loggers.foreach(l => l.transmit(level, message, details))
  }

  def receive(level: Level, message: String, details: Iterable[Details] = Nil): Unit = {
    loggers.foreach(l => l.receive(level, message, details))
  }

}
