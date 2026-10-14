// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.api.Options
import io.stepfunc.dnp3c.conformance.api.options.{ AnalogInputOptions, OutstationOptions, SupportOptions }
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.{ AppFunction, Message }
import io.stepfunc.dnp3c.dnp3.app.objects.Group32Var0
import io.stepfunc.dnp3c.dnp3.link.LinkFrame
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// 3.1r1's 8.19.2.4: an analog input goes up by 150% of its deadband, and back before the first event is confirmed
@RunWith(classOf[JUnitRunner])
class AnalogValueReturnTest extends AnyFunSuite with Matchers {

  /**
   * Analog input 0, starting at 100, whose plugin applies values directly; it reports an event per change, keeping
   * every unconfirmed one if queues is set, else only the latest, and asks for confirmation of a response with events
   */
  private class Dut(queues: Boolean) extends RespondingDriver {
    private var value = 100
    private var pending = List.empty[Int]
    private var awaiting: Option[(Int, Int)] = None // the sequence number and the events of the unconfirmed response

    override protected val testPlugin: DefaultIntegrationPlugin = new DefaultIntegrationPlugin {
      override def setAnalogInput(reporter: PluginReporter, index: Int, applied: Double): Unit = {
        value = applied.toInt
        pending = if (queues) pending :+ value else List(value)
      }
    }

    private def int32(v: Int): String = (0 to 3).map(i => "%02X".format((v >> (8 * i)) & 0xFF)).mkString(" ")

    override def writeMessage(message: Message, options: LinkOverride): Unit = {
      val seq = message.ctrl.seq.value.toInt
      if (message.function == AppFunction.confirm) {
        awaiting.filter(_._1 == seq).foreach { (_, sent) => pending = pending.drop(sent) }
        awaiting = None
      } else if (message.headers.exists(_.variation == Group32Var0)) {
        if (pending.isEmpty) enqueue("%02X 81 00 00".format(0xC0 | seq))
        else {
          val objects = pending.map(v => s"00 01 ${int32(v)}").mkString(" ")
          enqueue("%02X 81 00 00 20 01 17 %02X %s".format(0xE0 | seq, pending.size, objects))
          awaiting = Some((seq, pending.size))
        }
      } else enqueue("%02X 81 00 00 1E 01 00 00 00 01 %s".format(
        0xC0 | seq,
        int32(value)))
    }

    override def readFrame(timeout: Option[Int]): Option[LinkFrame] = timedOut(timeout)
  }

  private def passes(dutQueues: Boolean, profileQueues: Boolean): Boolean = {
    val dut = new Dut(dutQueues)
    val options = Options.from(Map(
      OutstationOptions.subsetLevel.id -> "2",
      AnalogInputOptions.valueReturnDeadband.id -> "10",
      SupportOptions.analogEventsQueuedPerPoint.id -> profileQueues.toString
    ))
    Run.passes(AnalogValueReturn.valueReturn, dut, options)
  }

  test("the second read reports two events or one, as the Device Profile says the DUT queues them") {
    passes(dutQueues = true, profileQueues = true) should equal(true)
    passes(dutQueues = false, profileQueues = false) should equal(true)
    passes(dutQueues = true, profileQueues = false) should equal(false)
    passes(dutQueues = false, profileQueues = true) should equal(false)
  }
}
