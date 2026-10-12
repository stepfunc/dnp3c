// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.link

import java.nio.ByteBuffer

import io.stepfunc.dnp3c.dnp3.{ LinkOverride, StubDriver }
import io.stepfunc.dnp3c.protocol.VirtualClock
import io.stepfunc.dnp3c.protocol.channel.ReadableChannel
import io.stepfunc.dnp3c.protocol.parsing.{ UInt16LE, UInt8 }
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class ValidatingLinkFrameReaderTest extends AnyFunSuite with Matchers {

  private val master = UInt16LE(1)
  private val outstation = UInt16LE(1024)

  // replays one frame per read, then times out
  private class Replay(frames: List[LinkFrame]) extends ReadableChannel {
    private var pending = frames.map(_.toBytes)
    override def read(buffer: ByteBuffer, timeoutMs: Int): Int = pending match {
      case bytes :: rest =>
        pending = rest
        buffer.put(bytes.toArray)
        bytes.size
      case Nil => 0
    }
  }

  private val data = List[Byte](0xC0.toByte, 0xC0.toByte, 0x81.toByte, 0x00, 0x00)

  private def fromOutstation(
      func: LinkFunction,
      data: List[Byte] = Nil,
      to: UInt16LE = master,
      fcb: Boolean = false): LinkFrame =
    LinkFrame.withData(Control(false, fcb, false, func), to, outstation, data)

  // a reader of one connection, over the frames, recording the frames it writes in reply
  private class Connection(frames: List[LinkFrame]) {
    var written = List.empty[LinkFrame]
    private val clock = new VirtualClock
    private val reader =
      new ValidatingLinkFrameReader(
        StubDriver.reporter(),
        new LinkFrameParser(new Replay(frames), clock),
        master,
        outstation,
        clock)(frame => written = written :+ frame)
    def read(linkOverride: LinkOverride = LinkOverride.default): Option[List[Byte]] =
      reader.read(FrameValidator.none, linkOverride)(1000)
  }

  private def read(
      frames: List[LinkFrame],
      linkOverride: LinkOverride = LinkOverride.default): (Option[List[Byte]], List[LinkFrame]) = {
    val connection = new Connection(frames)
    (connection.read(linkOverride), connection.written)
  }

  test("Answers a link status request and keeps reading") {
    val (result, written) =
      read(List(
        fromOutstation(LinkFunction.REQUEST_LINK_STATES),
        fromOutstation(LinkFunction.UNCONFIRMED_USER_DATA, data)))

    result should equal(Some(data))
    written should equal(List(LinkFrame(LinkHeader(
      UInt8(5),
      Control(true, false, false, LinkFunction.LINK_STATUS),
      outstation,
      master))))
  }

  test("Answers a link status request from the overridden destination address") {
    val other = UInt16LE(2)
    val (_, written) =
      read(List(fromOutstation(LinkFunction.REQUEST_LINK_STATES, to = other)), LinkOverride.destination(other))

    written.map(_.header.src) should equal(List(other))
  }

  test("Accepts and acknowledges a confirmed frame when the read overrides the control byte") {
    val (result, written) =
      read(List(fromOutstation(LinkFunction.CONFIRMED_USER_DATA, data, fcb = true)), LinkOverride.control(0x53))

    result should equal(Some(data))
    written.map(_.header.ctrl.func) should equal(List(LinkFunction.ACK))
  }

  test("Expects the FCB to start at 1 and alternate on each acknowledged frame") {
    val connection = new Connection(List(
      fromOutstation(LinkFunction.CONFIRMED_USER_DATA, data, fcb = true),
      fromOutstation(LinkFunction.CONFIRMED_USER_DATA, data, fcb = false),
      fromOutstation(LinkFunction.CONFIRMED_USER_DATA, data, fcb = false)
    ))
    connection.read() should equal(Some(data))
    connection.read() should equal(Some(data))
    an[Exception] should be thrownBy connection.read()
    connection.written.map(_.header.ctrl.func) should equal(List(LinkFunction.ACK, LinkFunction.ACK))
  }

  test("Expects the first FCB to be 1") {
    an[Exception] should be thrownBy read(List(fromOutstation(LinkFunction.CONFIRMED_USER_DATA, data, fcb = false)))
  }

  test("Acknowledges a confirmed frame from the overridden destination address") {
    val other = UInt16LE(2)
    val (_, written) =
      read(
        List(fromOutstation(LinkFunction.CONFIRMED_USER_DATA, data, to = other, fcb = true)),
        LinkOverride.destination(other))

    written.map(w => (w.header.ctrl.func, w.header.src, w.header.dest)) should equal(
      List((LinkFunction.ACK, other, outstation)))
  }

  test("Throws on a frame from an unknown source instead of reporting a timeout") {
    val frame =
      LinkFrame.withData(Control(false, false, false, LinkFunction.UNCONFIRMED_USER_DATA), master, UInt16LE(7), Nil)
    an[Exception] should be thrownBy read(List(frame))
  }

  test("Returns None when nothing is received") {
    read(Nil)._1 should equal(None)
  }

}
