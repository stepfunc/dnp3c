// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3

import io.stepfunc.dnp3c.dnp3.app.{ Message, ObjectHeader }
import io.stepfunc.dnp3c.dnp3.link.LinkFrame
import io.stepfunc.dnp3c.dnp3.transport.Transport
import io.stepfunc.dnp3c.protocol.Hex
import io.stepfunc.dnp3c.protocol.logging.{ Details, Level, Logger }

object Aliases {
  val physical: Level = Level.One
  val link: Level = Level.Two
  val transport: Level = Level.Three
  val app: Level = Level.Four
}

object Segment {

  private val headerBytes = 10
  private val maxBytesPerLine = 18

  def segment(bytes: List[Byte]): (String, List[Details]) = {
    val header = Hex.convert(bytes.take(headerBytes))
    val segments: List[String] = bytes.drop(headerBytes).grouped(maxBytesPerLine).map(x => Hex.convert(x)).toList
    (header, if (segments.isEmpty) Nil else List(Details(segments, Nil)))
  }

}

/**
 * When a plugin checks something it's asked to check
 */
sealed trait CheckTiming

object CheckTiming {

  // check now
  case object Immediate extends CheckTiming

  // the check may be answered after the procedure's steps, because answering it now could hold up a timed step
  case object Deferrable extends CheckTiming
}

/**
 * When a plugin performs a timed action
 */
sealed trait ActionTiming

object ActionTiming {

  // act now, before returning
  case object Now extends ActionTiming

  // an action whose effect the harness must see as it happens: the plugin may return before acting, as long as it
  // acts within withinMs, while the harness waits for the effect
  case class ArmThenAct(withinMs: Int) extends ActionTiming

  // an action inside a window that is already running: it must be done within withinMs, before returning
  case class ActBefore(withinMs: Int) extends ActionTiming
}

class PluginReporter(logger: Logger) {
  def log(msg: String): Unit = {
    logger.plugin(msg)
  }
}

// the reporter of a check, which says when the plugin may check
final class CheckReporter(logger: Logger, val timing: CheckTiming) extends PluginReporter(logger)

object TestReporter {
  def apply(logger: Logger): TestReporter = new TestReporter(logger)
  def empty: TestReporter = TestReporter(Logger.empty)
}

// logs what the harness does, e.g. for the link and transport layers
class TestReporter(val logger: Logger) {

  def info(msg: String): Unit = logger.info(msg)

  def warn(msg: String): Unit = logger.warn(msg)

  def error(msg: String): Unit = logger.error(msg)

  def note(msg: String, steps: List[String]): Unit = {
    logger.note(msg, List(Details(steps, Nil)))
  }

  def receive(frame: LinkFrame): Unit = {

    val (first, details) = Segment.segment(frame.toBytes)
    logger.receive(Aliases.physical, first, details)

    logger.receive(Aliases.link, frame.header.toString, Nil)
  }

  def receive(header: Transport.Header): Unit = {
    logger.receive(Aliases.transport, header.toString)
  }

  def receive(message: Message): Unit = {
    logger.receive(Aliases.app, message.toString, message.headers.map(h => details(h)))
  }

  def transmit(data: List[Byte]): Unit = {
    val (first, details) = Segment.segment(data)
    logger.transmit(Aliases.physical, first, details)
  }

  def transmit(frame: LinkFrame): Unit = {
    logger.transmit(Aliases.link, frame.toString, Nil)
  }

  def transmit(header: Transport.Header): Unit = {
    logger.transmit(Aliases.transport, header.toString)
  }

  def transmit(message: Message): Unit = {
    logger.transmit(Aliases.app, message.toString, message.headers.map(h => details(h)))
  }

  def pluginReporter(): PluginReporter = new PluginReporter(this.logger)

  def checkReporter(timing: CheckTiming): CheckReporter = new CheckReporter(this.logger, timing)

  private def details(header: ObjectHeader): Details = {
    Details(List(header.toString), header.contents.map(line => Details(List(line), Nil)).toList)
  }
}

// the reporter of a running procedure, which reaches that procedure's integration plugin
final class ProcedureReporter(logger: Logger, val plugin: IntegrationPlugin) extends TestReporter(logger) {

  private val noted = scala.collection.mutable.ListBuffer.empty[String]
  private val warned = scala.collection.mutable.ListBuffer.empty[String]

  /**
   * Note something about the result that a reader of the report should see, e.g. an answer the procedure allows as an
   * alternative. The note goes in the log now, and with the result if the procedure passes.
   */
  def note(text: String): Unit = {
    noted += text
    info("Note on the result: " + text)
  }

  def notes: List[String] = noted.toList

  /**
   * Warn of something about a pass that the device should probably change, though the procedure passes it, e.g. that
   * it complies with IEEE 1815-2012 but not 1815-202x. The warning goes in the log now, and with the result if the
   * procedure passes.
   */
  def warning(text: String): Unit = {
    warned += text
    warn("Warning on the result: " + text)
  }

  def warnings: List[String] = warned.toList
}
