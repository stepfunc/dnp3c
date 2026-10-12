// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8

import io.stepfunc.dnp3c.conformance.api.{ Options, TestProcedure }
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import java.io.StringReader
import scala.collection.mutable
import scala.util.Try

// what each procedure expects of the events it asks the plugin to check
@RunWith(classOf[JUnitRunner])
class EventExpectationsTest extends AnyFunSuite with Matchers {

  // passes every check, recording what each event check expected
  private class Plugin extends DefaultIntegrationPlugin {
    val expected = mutable.ListBuffer.empty[(String, EventExpectation)]
    override def generateInputChanges(reporter: PluginReporter, kind: InputKind[?]): Unit = ()
    override def generateClassEvents(reporter: PluginReporter, eventClass: EventClass): Unit = ()
    override def verifyInputEvents[P <: MeasurementPoint](
        reporter: CheckReporter,
        kind: InputKind[P],
        expectation: EventExpectation,
        points: Seq[GenIndexedPoint[P]]): Option[DeferredCheck] = {
      expected += ((kind.name, expectation))
      None
    }
    override def verifyClassEvents(
        reporter: CheckReporter,
        eventClass: EventClass,
        expectation: EventExpectation,
        points: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck] = {
      expected += ((eventClass.toString, expectation))
      None
    }
    override def verifyAllStaticCurrentValues(
        reporter: CheckReporter,
        isClass0: Boolean,
        points: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck] = None
  }

  // answers a read of counters (g22) or analogs (g32) with one such event, any other request with one binary event and
  // a static value, each asking for confirmation
  private class Dut(val plugin: Plugin = new Plugin) extends StubDriver(StubDriver.reporter(plugin)) {
    private val responses = mutable.Queue.empty[Message]
    override def writeMessage(message: Message, options: LinkOverride): Unit =
      if (message.function != AppFunction.confirm) {
        val objects = message.headers.headOption.map(_.variation.group.id.toInt) match {
          case Some(22) => "16 01 17 01 05 01 0A 00 00 00"
          case Some(32) => "20 01 17 01 05 01 0A 00 00 00"
          case _ => "02 01 17 01 05 81 01 02 00 00 00 81"
        }
        val control = Control(First.Yes, Final.Yes, Confirm.Yes, Unsolicited.No, message.ctrl.seq)
        val headers = Message.parse(Hex.from("C0 81 00 00 " + objects)).headers
        responses.enqueue(Message(control, AppFunction.response, Some(IIN.empty), headers))
      }
    override def readMessage(
        unsolicited: Unsolicited,
        validator: FrameValidator,
        linkOverride: LinkOverride,
        timeoutMs: Option[Int]): Option[Message] =
      if (responses.isEmpty) timedOut(timeoutMs) else Some(responses.dequeue())
    override def close(): Unit = ()
  }

  private def expectations(procedure: TestProcedure): List[(String, EventExpectation)] = {
    val dut = new Dut
    Console.withIn(new StringReader(""))(
      procedure.configure(Options.from(Map())).steps.foreach(s => Try(s.step.run(dut))))
    dut.plugin.expected.toList
  }

  test("a counter or analog response without confirmation reports current values, earlier ones optional") {
    expectations(s817.Procedure81725).last should equal(("counter", EventExpectation.CurrentValuesWithOptionalEarlier))
    expectations(s819.Procedure81922).last should equal(
      ("analog input", EventExpectation.CurrentValuesWithOptionalEarlier))
  }

  test("8.20.2 checks only the events returned, and 8.5.5.2.2 the batch generated") {
    val all = EventClass.All.toString
    expectations(s820.Procedure8202) should equal(List((all, EventExpectation.ReturnedEventsOnly)))
    expectations(s85.s855.Procedure85522) should equal(List((all, EventExpectation.LatestBatch)))
  }

  test("8.17.2.2 checks the first batch's events, then the recorded counts plus those generated since") {
    expectations(s817.Procedure81722) should equal(
      List(("counter", EventExpectation.LatestBatch), ("counter", EventExpectation.CurrentValuesWithOptionalEarlier)))
  }
}
