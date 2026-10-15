// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import scala.collection.mutable

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.api.Options
import io.stepfunc.dnp3c.conformance.api.options.{ LinkOptions, SupportOptions }
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.{ AppFunction, Message, Sequence, Unsolicited }
import io.stepfunc.dnp3c.dnp3.link.{ Control, LinkFrame, LinkFrameParser, LinkFunction }
import io.stepfunc.dnp3c.dnp3.transport.Transport
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// 3.1r1's 6.6.2d: an application layer confirm to another address, then with no gap a READ to the DUT
@RunWith(classOf[JUnitRunner])
class NoInterFrameGapTest extends AnyFunSuite with Matchers {

  /**
   * Answers a READ of class 0 with binary inputs 0 and 1, or, if it was preceded in the same write by a frame to
   * another address and the DUT is confused by that, with only binary input 0
   */
  private class Dut(confused: Boolean, supplied: Option[ProcedureReporter] = None)
      extends RespondingDriver(supplied) {
    val frames = mutable.ListBuffer.empty[LinkFrame]
    val writes = mutable.ListBuffer.empty[List[Byte]]

    private def answer(read: Message, both: Boolean): Unit = {
      val seq = read.ctrl.seq.value.toInt
      val objects = if (both) "01 02 00 00 01 01 01" else "01 02 00 00 00 01"
      enqueue("%02X 81 00 00 %s".format(0xC0 | seq, objects))
    }

    override def writeMessage(message: Message, options: LinkOverride): Unit = answer(message, both = true)

    override def write(bytes: List[Byte]): Unit = {
      writes += bytes
      // the frames of the write, in order
      var rest = bytes.toIndexedSeq
      while (rest.nonEmpty) LinkFrameParser.decode(rest, reporter) match {
        case LinkFrameParser.Decoded.Frame(frame, length) =>
          frames += frame
          rest = rest.drop(length)
        case other => throw new Exception(s"Unexpected $other")
      }
      val reads = frames.filter(_.header.dest == remote).map(f => Message.parse(f.data.tail))
      val toOther = frames.exists(_.header.dest != remote)
      reads.foreach(answer(_, both = !(confused && toOther)))
    }

    override def readFrame(timeout: Option[Int]): Option[io.stepfunc.dnp3c.dnp3.link.LinkFrame] =
      if (!responseWaiting) timedOut(timeout) else throw new Exception("an extra response")
  }

  private val options =
    Options.from(Map(SupportOptions.multipleAddresses.id -> "true", LinkOptions.otherDestination.id -> "1025"))

  private def run(dut: Dut): Boolean =
    Run.passes(NoInterFrameGap.noInterFrameGap, dut, options)

  test("one write carries the confirm to the other address, then the READ to the DUT") {
    val dut = new Dut(confused = false)
    run(dut) should equal(true)
    dut.frames.map(_.header.dest.value).toList should equal(List(1025, 1024))
    Message.parse(dut.frames.head.data.tail).function should equal(AppFunction.confirm)
    Message.parse(dut.frames(1).data.tail).function should equal(AppFunction.read)
  }

  test("the write carries the frames as built by hand, each logged at the application, transport and link layers") {
    val log = new RecordingLogger
    val dut = new Dut(confused = false, Some(new ProcedureReporter(log, new DefaultIntegrationPlugin())))
    run(dut) should equal(true)

    val confirm = Requests.confirm(Sequence.zero, Unsolicited.No)
    val read = Requests.readClass0(Sequence.one)
    def frame(destination: Int, message: Message, seq: Byte) = LinkFrame.withData(
      Control(master = true, fcb = false, fcv = false, LinkFunction.UNCONFIRMED_USER_DATA),
      UInt16LE(destination),
      dut.local,
      Transport.function(fir = true, fin = true, seq) :: message.toBytes
    )
    val frames = List(frame(1025, confirm, 0), frame(1024, read, 1))

    dut.writes.toList should equal(List(frames.flatMap(_.toBytes)))
    log.transmitted(Aliases.app) should equal(List(confirm.toString, read.toString))
    log.transmitted(Aliases.transport) should equal(
      List(Transport.Header(true, true, 0).toString, Transport.Header(true, true, 1).toString))
    log.transmitted(Aliases.link) should equal(frames.map(_.toString))
  }

  test("a DUT that answers the READ differently after the confirm fails") {
    run(new Dut(confused = true)) should equal(false)
  }

  test("it runs only for a DUT with more than one address") {
    NoInterFrameGap.noInterFrameGap.disabledBy(Options.from(Map())) should not equal None
    NoInterFrameGap.noInterFrameGap.disabledBy(options) should equal(None)
  }

  test("the other address must be another than the DUT's") {
    val same = Options.from(
      Map(SupportOptions.multipleAddresses.id -> "true", LinkOptions.otherDestination.id -> "1024"))
    an[Exception] should be thrownBy NoInterFrameGap.noInterFrameGap.configure(same).steps
  }
}
