// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.Placements.id
import io.stepfunc.dnp3c.conformance.api.ProcedureDefinition
import io.stepfunc.dnp3c.conformance.api.options.TimeSyncOptions
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.protocol.{ Hex, VirtualClock }
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

// relative-time events and the CTOs they're timed from
@RunWith(classOf[JUnitRunner])
class CtoChecksTest extends AnyFunSuite with Matchers {

  // generates and accepts binary input events, recording when each batch is generated on the DUT's clock
  private class Plugin extends DefaultIntegrationPlugin {
    var clock: VirtualClock = new VirtualClock
    val generatedAt = mutable.ListBuffer.empty[Long]
    override def cyclePower(reporter: PluginReporter): Unit = ()
    override def generateInputChanges(reporter: PluginReporter, kind: InputKind[?]): Unit = generatedAt += clock.nowMs()
    override def verifyInputEvents[P <: MeasurementPoint](
        reporter: CheckReporter,
        kind: InputKind[P],
        expectation: EventExpectation,
        points: Seq[GenIndexedPoint[P]]): Option[DeferredCheck] = None
  }

  // answers a READ of events with the objects, asking for confirmation, and any other request with a null response
  private class Dut(objects: String, val plugin: Plugin = new Plugin)
      extends RespondingDriver(StubDriver.reporter(plugin)) {
    plugin.clock = clock
    override def writeMessage(message: Message, options: LinkOverride): Unit = message.function match {
      case AppFunction.confirm =>
      case AppFunction.read =>
        val headers = Message.parse(Hex.from("C0 81 00 00 " + objects)).headers
        val control = Control(First.Yes, Final.Yes, Confirm.Yes, Unsolicited.No, message.ctrl.seq)
        enqueue(Message(control, AppFunction.response, Some(IIN.empty), headers))
      case _ =>
        enqueue(Message(Control.request(message.ctrl.seq), AppFunction.response, Some(IIN.empty), Nil))
    }
  }

  // runs every step, carrying on past failures; a manual question gets no answer (end of input), so it fails rather
  // than waits. Returns how many steps failed.
  private def failedSteps(procedure: ProcedureDefinition, dut: Dut, settings: Map[String, String] = Map()): Int =
    Run.everyStep(procedure, dut, settings)

  private val unsynchronizedCto = "33 02 07 01 00 00 00 00 00 00" // g51v2, qualifier 0x07
  private val event = "02 03 17 01 05 81 10 00" // g2v3, qualifier 0x17, index 5

  test("8.15.2 checks the qualifiers and the variation of the CTO and the events timed from it") {
    def failures(objects: String) = failedSteps(
      Procedure8_15_2,
      new Dut(objects),
      Map(TimeSyncOptions.supportSynchronizingTimeFromMaster.id -> "false"))
    val good = failures(s"$unsynchronizedCto $event")
    failures("33 02 08 01 00 00 00 00 00 00 00 " + event) should be > good // CTO with qualifier 0x08
    failures(s"$unsynchronizedCto 02 03 00 05 05 81 10 00") should be > good // events with qualifier 0x00
    failures(s"33 01 07 01 00 00 00 00 00 00 $event") should be > good // synchronized CTO before the time is set
    failures(unsynchronizedCto) should be > good // no events
  }

  test("8.14.2.15 generates its two event batches well over 0xFFFF ms apart") {
    val dut = new Dut(s"$unsynchronizedCto $event")
    failedSteps(Procedure8_14_2_15, dut)
    val List(first, second) = dut.plugin.generatedAt.toList
    (second - first) should be >= (0xFFFFL + 5000)
  }
}
