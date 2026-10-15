// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3

import io.stepfunc.dnp3c.protocol.logging.{ Details, Level, Logger }

import scala.collection.mutable

// a logger for tests that keeps what's transmitted, by layer
class RecordingLogger extends Logger {
  private val sent = mutable.ListBuffer.empty[(Level, String)]

  // the lines transmitted at the layer, in order
  def transmitted(level: Level): List[String] = sent.filter(_._1 == level).map(_._2).toList

  def info(lines: String): Unit = ()
  def warn(lines: String): Unit = ()
  def error(lines: String): Unit = ()
  def note(message: String, details: Iterable[Details]): Unit = ()
  def plugin(message: String): Unit = ()
  def transmit(level: Level, message: String, details: Iterable[Details]): Unit = sent += ((level, message))
  def receive(level: Level, message: String, details: Iterable[Details]): Unit = ()
}
