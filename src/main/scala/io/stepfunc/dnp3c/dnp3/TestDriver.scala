// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3

import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.link._
import io.stepfunc.dnp3c.dnp3.transport.SegmentWriter
import io.stepfunc.dnp3c.protocol.{ Clock, Hex }
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE

import java.net.InetSocketAddress
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

/**
 * A way for the harness to talk to the DUT, from a local address and port: port 0 lets the system choose
 */
sealed trait Connection {
  def remote: InetSocketAddress
  def local: InetSocketAddress
}

/** a TCP connection to the DUT */
final case class TcpConnection(remote: InetSocketAddress, local: InetSocketAddress) extends Connection

/** datagrams to the DUT, sent from the local address and port, where the DUT's datagrams are received */
final case class UdpConnection(remote: InetSocketAddress, local: InetSocketAddress) extends Connection

/** What became of a connection the DUT shouldn't accept */
enum ProbeResult {
  // the DUT refused it
  case Refused
  // it wasn't accepted within the response timeout
  case TimedOut
  // the DUT accepted it, then closed or reset it without answering a REQUEST LINK STATUS
  case Closed
  // the DUT accepted it and kept it open, without answering a REQUEST LINK STATUS
  case Silent
  // the DUT accepted it and answered a REQUEST LINK STATUS
  case Answered
}

trait TestDriver {

  // configuration

  def reporter: ProcedureReporter

  // the revision's rules for the messages the procedure receives; none unless the driver enforces them
  def policies: Policies = Policies.none

  def local: UInt16LE

  def remote: UInt16LE

  // ---- physical layer ----

  def write(bytes: List[Byte]): Unit

  // closes the connection; a UDP connection's socket stays bound, as UDP has no connection to close
  def close(): Unit

  // closes everything, once the procedure is done
  def shutdown(): Unit = close()

  // the time and waits of the run: every timed read and wait goes through it, so tests can use virtual time
  def clock: Clock

  // ---- network (3.1r1's section 9) ----

  // Makes the connection current, opening it now unless an earlier connect kept it open; what follows uses it, as does
  // a reconnection after close(). The current connection closes, unless keep, which keeps it open for a later connect
  // to make current again. Returns the local address it's from. Throws if the DUT doesn't accept a TCP connection, and
  // LocalBindException if the harness can't use the local address.
  def connect(connection: Connection, keep: Boolean = false): InetSocketAddress =
    throw new UnsupportedOperationException("this driver can't make its own connections")

  // Discards the datagrams that have arrived at an open UDP connection and haven't been read, and counts them
  def drainDatagrams(connection: UdpConnection): Int =
    throw new UnsupportedOperationException("this driver can't make its own connections")

  // Sends the message as datagrams, one per link frame, from the local address to the remote one (which may be a
  // multicast group), alongside the driver's connection, which it leaves as it is
  def sendDatagrams(
      remote: InetSocketAddress,
      local: InetSocketAddress,
      message: Message,
      options: LinkOverride): Unit =
    throw new UnsupportedOperationException("this driver can't send datagrams")

  // Tries a connection the DUT shouldn't accept, alongside the driver's own: if the DUT accepts it, sends a REQUEST LINK
  // STATUS and waits the link timeout for an answer. Then closes it.
  def probe(connection: TcpConnection): ProbeResult =
    throw new UnsupportedOperationException("this driver can't make its own connections")

  // ---- link layer ----

  def writeFrame(frame: LinkFrame): Unit

  def readFrame(timeout: Option[Int] = None): Option[LinkFrame]

  // reads a frame, with when its first byte arrived, by the clock, in nanoseconds
  def readFrameTimed(timeout: Option[Int] = None): Option[(LinkFrame, Long)] =
    throw new UnsupportedOperationException("this driver can't time frames")

  // asserts or clears RTS on a serial line
  def setRts(asserted: Boolean): Unit =
    throw new UnsupportedOperationException("this driver has no serial line")

  // the bytes writeMessage sends for the message: its link frames
  def frameBytes(message: Message, options: LinkOverride = LinkOverride.default): List[Byte] = {
    val bytes = scala.collection.mutable.ListBuffer.empty[Byte]
    SegmentWriter.write(message.toBytes, 0, reporter) { segment =>
      LinkWriter.write(local, options.destination.getOrElse(remote), true, options.controlBlock, reporter)(segment) {
        frame => bytes ++= frame
      }
    }
    bytes.toList
  }

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
