// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import scala.collection.mutable
import scala.util.Try

import io.stepfunc.dnp3c.conformance.api.Options
import io.stepfunc.dnp3c.conformance.api.options.{ CounterOptions, OutstationOptions }
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.link.{ FrameValidator, LinkFrame }
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// 3.1r1's 8.25 for counter events
@RunWith(classOf[JUnitRunner])
class EventVariationsTest extends AnyFunSuite with Matchers {

  /**
   * Counters 0 and 1; each generation makes an event of each, which the DUT keeps until a read's response is confirmed,
   * and reports in the variation requested, or always 32-bit if wide, or with IIN2.1 if it has no counter events
   */
  private class Dut(wide: Boolean = false, noEvents: Boolean = false, iin: Int = 0x02)
      extends StubDriver(StubDriver.reporter()) {
    private var pending = List.empty[Int]
    private var awaiting: Option[Int] = None
    private val responses = mutable.Queue.empty[Message]

    val plugin: DefaultIntegrationPlugin = new DefaultIntegrationPlugin {
      override def setFlagsNormal(reporter: PluginReporter, kind: PointKind): Unit = ()
      override def generateExactInputEvents(reporter: PluginReporter, kind: InputKind[?], numEvents: Int): Unit =
        pending = pending ++ List(0, 1)
      override def verifyInputEvents[P <: MeasurementPoint](
          reporter: CheckReporter,
          kind: InputKind[P],
          expectation: EventExpectation,
          points: Seq[GenIndexedPoint[P]]): Option[DeferredCheck] = None
    }
    override val reporter: ProcedureReporter = StubDriver.reporter(plugin)

    override def writeMessage(message: Message, options: LinkOverride): Unit = {
      val seq = message.ctrl.seq.value.toInt
      if (message.function == AppFunction.confirm) {
        if (awaiting.contains(seq)) pending = Nil
        awaiting = None
      } else if (noEvents) responses.enqueue(Message.parse(Hex.from("%02X 81 00 %02X".format(0xC0 | seq, iin))))
      else {
        val variation = if (wide) 1 else message.headers.head.variation.id.toInt
        val objects = pending.map(i =>
          if (variation == 1) "%02X 01 05 00 00 00".format(i) else "%02X 01 05 00".format(i)).mkString(" ")
        responses.enqueue(Message.parse(
          Hex.from("%02X 81 00 00 16 %02X 17 %02X %s".format(0xE0 | seq, variation, pending.size, objects))))
        awaiting = Some(seq)
      }
    }

    override def readMessage(
        unsolicited: Unsolicited,
        validator: FrameValidator,
        linkOverride: LinkOverride,
        timeoutMs: Option[Int]): Option[Message] =
      if (responses.isEmpty) timedOut(timeoutMs) else Some(responses.dequeue())
    override def readFrame(timeout: Option[Int]): Option[LinkFrame] = timedOut(timeout)
    override def close(): Unit = {}
  }

  private def run(dut: Dut, level: Int = 3, settings: Map[String, String] = Map()): Boolean = {
    val options = Options.from(settings + (OutstationOptions.subsetLevel.id -> level.toString))
    Try(
      EventVariations.test(EventVariations.counters).configure(options).steps.foreach(s =>
        s.step.run(dut).foreach(_.check()))).isSuccess
  }

  test("events in the variations requested pass, read again unconfirmed") {
    run(new Dut) should equal(true)
  }

  test("32-bit events for a 16-bit request pass only for counters the DUT keeps only as 32 bits") {
    run(new Dut(wide = true)) should equal(false)
    run(new Dut(wide = true), settings = Map(CounterOptions.only32Bit.id -> "0, 1")) should equal(true)
  }

  test("IIN2.1 passes at level 2, or without counter events, but not at level 3 with them") {
    run(new Dut(noEvents = true), level = 2) should equal(true)
    run(new Dut(noEvents = true), level = 3) should equal(false)
    run(new Dut(noEvents = true), settings = Map(Inputs.counter.events.id -> "false")) should equal(true)
  }

  test("at level 3, a device with counters but without their events may answer IIN2.1, but not IIN2.2") {
    run(new Dut(noEvents = true, iin = 0x04), settings = Map(Inputs.counter.events.id -> "false")) should equal(false)
    run(new Dut(noEvents = true, iin = 0x04), settings = Map(Inputs.counter.points.id -> "false")) should equal(true)
  }
}
