// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3

import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.link._
import io.stepfunc.dnp3c.protocol.{ Clock, Hex }
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE

import scala.annotation.tailrec

sealed trait AutoConfirm {
  def execute(msg: Message, driver: TestDriver): Unit

  // what a step that reads a response does to confirm it, for the step's description
  def description: List[Comment] = Nil
}

object AutoConfirm {
  object Yes extends AutoConfirm {
    override def description: List[Comment] = List("Confirm each response that asks for a confirmation")
    override def execute(msg: Message, driver: TestDriver): Unit = {
      driver.writeMessage(Requests.confirm(msg.ctrl.seq, msg.ctrl.uns))
    }
  }

  object No extends AutoConfirm {
    override def execute(msg: Message, driver: TestDriver): Unit = {}
  }

  object Wrong extends AutoConfirm {
    override def description: List[Comment] =
      List("Answer a response that asks for a confirmation with a confirmation of the wrong sequence number")
    override def execute(msg: Message, driver: TestDriver): Unit = {
      driver.writeMessage(Requests.confirm(msg.ctrl.seq.next, msg.ctrl.uns))
    }
  }

  class Custom(executor: (Message, TestDriver) => Unit, override val description: List[Comment] = Nil)
      extends AutoConfirm {
    override def execute(msg: Message, driver: TestDriver): Unit = {
      executor(msg, driver)
    }
  }
}

/**
 * Overrides of the link layer's defaults. Writing, destination is the address sent to instead of the DUT's, and
 * controlBlock is the control byte sent. Reading, destination is the address the DUT is expected to send to instead
 * of the master's (it is also the source of the link frames sent in reply), and controlBlock is not used.
 */
case class LinkOverride(controlBlock: Option[Byte], destination: Option[UInt16LE]) {

  def any: Boolean = controlBlock.isDefined || destination.isDefined

  override def toString: String = {
    val optionList: String = List(
      controlBlock.map(x => s"control == ${Hex.prefixed(x)}"),
      destination.map(d => s"destination == ${Hex.prefixed(d)}")
    ).flatten.mkString(", ")
    s"{ $optionList }"
  }

  def withDestination(destination: UInt16LE): LinkOverride = copy(destination = Some(destination))
  def withControlBlock(controlBlock: Byte): LinkOverride = copy(controlBlock = Some(controlBlock))
}

object LinkOverride {
  val default: LinkOverride = LinkOverride(None, None)
  def control(value: Byte): LinkOverride = default.withControlBlock(value)
  def destination(value: UInt16LE): LinkOverride = default.withDestination(value)
}

trait TestDriver {

  // configuration

  def reporter: ProcedureReporter

  def local: UInt16LE

  def remote: UInt16LE

  // ---- physical layer ----

  def write(bytes: List[Byte]): Unit

  def close(): Unit

  // the time and waits of the run: every timed read and wait goes through it, so tests can use virtual time
  def clock: Clock

  // ---- link layer ----

  def writeFrame(frame: LinkFrame): Unit

  def readFrame(timeout: Option[Int] = None): Option[LinkFrame]

  // ---- application layer ----

  def writeMessage(message: Message, options: LinkOverride = LinkOverride.default): Unit

  // None means nothing was received before the timeout; anything invalid that is received throws.
  // The timeout defaults to the configured (unsolicited) response timeout.
  def readMessage(
      unsolicited: Unsolicited,
      validator: FrameValidator = FrameValidator.none,
      linkOverride: LinkOverride = LinkOverride.default,
      timeoutMs: Option[Int] = None): Option[Message]

  def readAllResponses(
      seq: Sequence,
      autoConfirm: AutoConfirm,
      func: app.AppFunction,
      validator: FrameValidator = FrameValidator.none): List[Message] = {

    @tailrec
    def recurse(list: List[Message], seq: Sequence): List[Message] = readMessage(Unsolicited.No, validator) match {
      case None => throw new Exception("Unable to read complete message within timeout")
      case Some(message) =>
        if (message.ctrl.con.value) autoConfirm.execute(message, this)

        TestDriver.checkSolicited(message, seq, func)
        if (message.ctrl.fir.value != list.isEmpty) throw new Exception("Unexpected fir bit: " + message.ctrl.fir)

        if (message.ctrl.fin.value) message :: list else recurse(message :: list, seq.next)
    }

    recurse(Nil, seq).reverse
  }
}

object TestDriver {

  // what every fragment of a solicited response must carry
  def checkSolicited(message: Message, seq: Sequence, function: AppFunction = AppFunction.response): Unit = {
    if (message.ctrl.uns.value) throw new Exception("Unexpected unsolicited response")
    if (message.ctrl.seq != seq) throw new Exception("Unexpected sequence: " + message.ctrl.seq)
    if (message.function != function) throw new Exception("Unexpected function code: " + message.function)
  }
}
