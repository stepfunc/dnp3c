// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import java.nio.ByteBuffer

import io.stepfunc.dnp3c.conformance.api.TestStep
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.link.{ Control, LinkFrame }
import io.stepfunc.dnp3c.dnp3.transport.Transport
import io.stepfunc.dnp3c.protocol.VirtualClock
import io.stepfunc.dnp3c.protocol.channel.{ ChannelFactory, ReadableWritableChannel }
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

// 6.6.2's class 0 reads: the bytes sent, and how they're logged
@RunWith(classOf[JUnitRunner])
class PrimaryFramesTest extends AnyFunSuite with Matchers {

  // a line that keeps each write
  private class Line extends ChannelFactory {
    val writes = mutable.ListBuffer.empty[List[Byte]]
    override def open(): ReadableWritableChannel = new ReadableWritableChannel {
      override def read(buffer: ByteBuffer, timeoutMs: Int): Int = 0
      override def write(buffer: ByteBuffer): Unit = {
        val bytes = new Array[Byte](buffer.remaining())
        buffer.get(bytes)
        writes += bytes.toList
      }
      override def close(): Unit = {}
    }
  }

  // runs the step against a driver on a line, and checks it wrote the bytes, logged once at each layer
  private def checkSent(step: TestStep, frame: LinkFrame, bytes: List[Byte]): Unit = {
    val log = new RecordingLogger
    val line = new Line
    val driver = new DefaultTestDriver(
      new ProcedureReporter(log, new DefaultIntegrationPlugin()),
      line,
      UInt16LE(1),
      UInt16LE(1024),
      1000,
      1000,
      1000,
      500,
      new VirtualClock)
    step.run(driver)
    line.writes.toList should equal(List(bytes))
    log.transmitted(Aliases.app) should equal(List(read.toString))
    log.transmitted(Aliases.transport) should equal(List(Transport.Header(true, true, 0).toString))
    log.transmitted(Aliases.link) should equal(List(frame.toString))
    log.transmitted(Aliases.physical) should equal(List(Segment.segment(bytes)._1))
  }

  private val read = Requests.readClass0(Sequence.zero)

  // the frame as the procedures built it by hand: one transport segment, from the master to the DUT
  private def handBuilt(control: Control): LinkFrame =
    LinkFrame.withData(
      control,
      UInt16LE(1024),
      UInt16LE(1),
      Transport.function(fir = true, fin = true, 0) :: read.toBytes)

  test("6.6.2.1 sends the read with other start octets") {
    val frame = handBuilt(Control(0xD3.toByte))
    checkSent(
      Procedure6_6_2_1.readClass0CustomStartOctets(0x09.toByte, 0x64.toByte),
      frame,
      frame.toBytesWithStart(0x09.toByte, 0x64.toByte))
  }

  test("6.6.2.2 sends the read with an undefined link function code") {
    val frame = handBuilt(Control(0xD5.toByte))
    checkSent(Procedure6_6_2_2.readClass0WithInvalidCode(0x05.toByte), frame, frame.toBytes)
  }
}
