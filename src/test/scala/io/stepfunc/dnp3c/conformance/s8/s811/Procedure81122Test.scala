// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s811

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class Procedure81122Test extends AnyFunSuite with Matchers {

  // records the event checks it's asked for: when each may run, and the analog values each saw
  private class Plugin extends DefaultIntegrationPlugin {
    val generatedChecks = mutable.ListBuffer.empty[(CheckTiming, List[Double])]
    val retainedChecks = mutable.ListBuffer.empty[(CheckTiming, List[Double], List[Double])]
    override def cyclePower(reporter: PluginReporter): Unit = ()
    override def enableUnsolicitedResponse(reporter: PluginReporter, enabled: Boolean): Unit = ()
    override def generateClassEvents(reporter: PluginReporter, eventClass: EventClass): Unit = ()
    override def verifyClassEvents(
        reporter: CheckReporter,
        eventClass: EventClass,
        expectation: EventExpectation,
        points: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck] = {
      generatedChecks += ((reporter.timing, values(points)))
      None
    }
    override def verifyResponseRetainsEvents(
        reporter: CheckReporter,
        previous: Seq[GenIndexedPoint[MeasurementPoint]],
        current: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck] = {
      retainedChecks += ((reporter.timing, values(previous), values(current)))
      None
    }
    private def values(points: Seq[GenIndexedPoint[MeasurementPoint]]): List[Double] =
      points.toList.collect { case GenIndexedPoint(_, p: AnalogPoint) => p.value }
  }

  // answers requests with Null Responses; sends `unsolicited` in turn, each a confirm timeout after the read for it
  private class Dut(plugin: Plugin, unsolicited: List[Option[String]])
      extends RespondingDriver(StubDriver.reporter(plugin)) {
    private var pending = unsolicited
    override def writeMessage(message: Message, options: LinkOverride): Unit =
      if (message.function != AppFunction.confirm)
        enqueue("%02X 81 00 00".format(0xC0 | message.ctrl.seq.value))
    override def readMessage(
        unsolicited: Unsolicited,
        validator: FrameValidator,
        linkOverride: LinkOverride,
        timeoutMs: Option[Int]): Option[Message] =
      if (!unsolicited.value) super.readMessage(unsolicited, validator, linkOverride, timeoutMs)
      else
        pending match {
          case Some(objects) :: rest =>
            pending = rest
            clock.advance(5000)
            Some(Message.parse(Hex.from("F0 82 00 00 " + objects)))
          case _ :: rest =>
            pending = rest
            timedOut(timeoutMs)
          case Nil => timedOut(timeoutMs)
        }
  }

  // g32v1: analog input 0 with a value, as a little-endian 32-bit integer
  private def analog(value: Int) = "20 01 17 01 00 01 %02X 00 00 00".format(value)

  test("each retry is checked against the response it repeats, which may update analog values (step 12)") {
    val plugin = new Plugin
    // the startup null response, none while disabled, the response and its two retries, then none
    val dut = new Dut(plugin, List(Some(""), None, Some(analog(10)), Some(analog(20)), Some(analog(20)), None))
    Run.passes(Procedure81122, dut) should equal(true)
    // the first response against the events generated, each retry against the response before it; the checks of the
    // responses a later retry is timed from may wait until the steps are done
    plugin.generatedChecks.toList should equal(List((CheckTiming.Deferrable, List(10.0))))
    plugin.retainedChecks.toList should equal(
      List(
        (CheckTiming.Deferrable, List(10.0), List(20.0)),
        (CheckTiming.Immediate, List(20.0), List(20.0))))
  }
}
