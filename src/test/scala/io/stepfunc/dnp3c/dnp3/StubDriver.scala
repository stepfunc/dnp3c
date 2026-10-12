// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3

import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.link.LinkFrame
import io.stepfunc.dnp3c.protocol.VirtualClock
import io.stepfunc.dnp3c.protocol.logging.Logger
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE

import scala.collection.mutable

// a driver for tests: every call fails unless the test overrides it
abstract class StubDriver(val reporter: ProcedureReporter) extends TestDriver {
  override def local: UInt16LE = UInt16LE(1)
  override def remote: UInt16LE = UInt16LE(1024)
  override def close(): Unit = ???

  // virtual time
  override val clock: VirtualClock = new VirtualClock

  // nothing arrives: as with a real read, the timeout passes
  protected def timedOut[A](timeoutMs: Option[Int]): Option[A] = {
    timeoutMs.foreach(ms => clock.advance(ms))
    None
  }
  override def write(bytes: List[Byte]): Unit = ???
  override def writeFrame(frame: LinkFrame): Unit = ???
  override def readFrame(timeout: Option[Int]): Option[LinkFrame] = ???
  override def writeMessage(message: Message, options: LinkOverride): Unit = ???
  override def readMessage(
      unsolicited: Unsolicited,
      validator: FrameValidator,
      linkOverride: LinkOverride,
      timeoutMs: Option[Int]): Option[Message] = ???
}

object StubDriver {
  def reporter(plugin: IntegrationPlugin = new DefaultIntegrationPlugin()): ProcedureReporter =
    new ProcedureReporter(Logger.empty, plugin)
}

// a DUT that answers requests from writeMessage; a read returns the oldest unread response, or nothing
abstract class RespondingDriver(reporter: ProcedureReporter) extends StubDriver(reporter) {
  private val responses = mutable.Queue.empty[Message]

  protected def respond(request: Message, iin: IIN, headers: List[ObjectHeader]): Unit =
    responses.enqueue(Message(Control.request(request.ctrl.seq), AppFunction.response, Some(iin), headers))

  override def readMessage(
      unsolicited: Unsolicited,
      validator: FrameValidator,
      linkOverride: LinkOverride,
      timeoutMs: Option[Int]): Option[Message] =
    if (responses.isEmpty) timedOut(timeoutMs) else Some(responses.dequeue())
}

// a DUT that answers each request with answer(request): its IIN and object headers; records the requests
class ScriptedDut(reporter: ProcedureReporter, answer: Message => (IIN, List[ObjectHeader]))
    extends RespondingDriver(reporter) {
  val requests = mutable.ListBuffer.empty[Message]

  override def writeMessage(message: Message, options: LinkOverride): Unit = {
    requests += message
    val (iin, headers) = answer(message)
    respond(message, iin, headers)
  }

  override def close(): Unit = {}
}
