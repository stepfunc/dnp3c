// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import scala.collection.mutable

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.api.Options
import io.stepfunc.dnp3c.conformance.api.options.SerialOptions
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.{ Message, Unsolicited }
import io.stepfunc.dnp3c.dnp3.link.{ Control, FrameValidator, LinkFrame }
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// 3.1r1's 8.10.2, with the DUT at address 1024 and other devices at 5 and 6
@RunWith(classOf[JUnitRunner])
class MultiDropTest extends AnyFunSuite with Matchers {

  /**
   * Answers reads to its address, and to the broadcast address if answersBroadcast; the other devices answer theirs,
   * which the harness sees on the drop too
   */
  private class Drop(answersBroadcast: Boolean = false, lateOther: Boolean = false)
      extends RespondingDriver {
    val delays: mutable.ListBuffer[Option[RtsDelays]] = mutable.ListBuffer.empty
    var carrierChecks = 0
    private val frames = mutable.Queue.empty[LinkFrame]

    override protected val testPlugin: DefaultIntegrationPlugin = new DefaultIntegrationPlugin {
      override def setRtsDelays(reporter: PluginReporter, d: Option[RtsDelays]): Unit = delays += d
      override def verifyNoCarrier(reporter: CheckReporter): Option[DeferredCheck] = None
      override def verifyCarrierTiming(reporter: CheckReporter, d: Option[RtsDelays]): Option[DeferredCheck] = {
        carrierChecks += 1
        None
      }
    }

    override def writeMessage(message: Message, options: LinkOverride): Unit = {
      val to = options.destination.getOrElse(remote).value
      val seq = message.ctrl.seq.value.toInt
      if (to == remote.value || (to == 0xFFFF && answersBroadcast)) {
        frames.enqueue(LinkFrame.headerOnly(Control(0x44.toByte), local, remote))
        if (to == remote.value) enqueue("%02X 81 00 00".format(0xC0 | seq))
      } else if (to != 0xFFFF) {
        frames.enqueue(LinkFrame.headerOnly(Control(0x44.toByte), local, UInt16LE(to)))
        // the other device's answer comes again later, after the harness has stopped waiting for the DUT
        if (lateOther) late.enqueue(LinkFrame.headerOnly(Control(0x44.toByte), local, UInt16LE(to)))
      }
    }

    private val late = mutable.Queue.empty[LinkFrame]

    // the late answers come only after the silence windows, which read with the response timeout: to a read with
    // the link timeout, the default
    override def readFrame(timeout: Option[Int]): Option[LinkFrame] =
      if (frames.nonEmpty) Some(frames.dequeue())
      else if (late.nonEmpty && timeout.isEmpty) Some(late.dequeue())
      else timedOut(timeout)

    // a message read takes the DUT's frames; any other device's frame in the way breaks it, as the real driver's
    // link validation does
    override def readMessage(
        unsolicited: Unsolicited,
        validator: FrameValidator,
        linkOverride: LinkOverride,
        timeoutMs: Option[Int]): Option[Message] = {
      if ((frames ++ late).exists(_.header.src != remote))
        throw new Exception("A frame from another device came with the DUT's response")
      frames.clear()
      super.readMessage(unsolicited, validator, linkOverride, timeoutMs)
    }
  }

  private val bench = Map(
    SerialOptions.enabled.id -> "true",
    SerialOptions.multiDrop.id -> "true",
    SerialOptions.otherDevices.id -> "5, 6")

  private def run(drop: Drop, settings: Map[String, String] = Map()): Boolean =
    Run.passes(MultiDrop.multiDrop, drop, bench ++ settings)

  test("the DUT answers its own reads only, while the other devices answer theirs") {
    val drop = new Drop
    run(drop) should equal(true)
    drop.carrierChecks should equal(1)
    run(new Drop(answersBroadcast = true)) should equal(false)
  }

  test("a late frame from another device before the DUT's read is waited out") {
    val drop = new Drop(lateOther = true)
    run(drop) should equal(true)
  }

  test("each RTS delay setting is tested, and the usual ones restored") {
    val drop = new Drop
    run(drop, Map(SerialOptions.rtsDelays.id -> "0/0, 10/5")) should equal(true)
    drop.delays.toList should equal(List(Some(RtsDelays(0, 0)), Some(RtsDelays(10, 5)), None))
    drop.carrierChecks should equal(3)
  }

  test("8.10.2 needs serial, a multi-drop DUT, two other devices and readable delays") {
    MultiDrop.multiDrop.disabledBy(Options.from(bench - SerialOptions.enabled.id)) should not equal None
    MultiDrop.multiDrop.disabledBy(Options.from(bench - SerialOptions.multiDrop.id)) should not equal None
    def problems(settings: Map[String, String]) =
      MultiDrop.multiDrop.configurationProblems(Options.from(bench ++ settings)).map(_.key)
    problems(Map()) should equal(Nil)
    problems(Map(SerialOptions.otherDevices.id -> "5")) should equal(List(SerialOptions.otherDevices.id))
    problems(Map(SerialOptions.otherDevices.id -> "5, 1024")) should equal(List(SerialOptions.otherDevices.id))
    for (bad <- List("10-5", "0/0,", ",", "2147483648/0", "0/0, 0/0"))
      problems(Map(SerialOptions.rtsDelays.id -> bad)) should equal(List(SerialOptions.rtsDelays.id))
    problems(Map(SerialOptions.otherDevices.id -> "65534, 65535")) should equal(List(SerialOptions.otherDevices.id))
    // with nothing set, the steps can still be described
    MultiDrop.multiDrop.configure(Options.from(bench - SerialOptions.otherDevices.id)).steps should not be empty
  }
}
