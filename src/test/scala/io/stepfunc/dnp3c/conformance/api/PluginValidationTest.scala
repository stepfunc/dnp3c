// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api

import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.protocol.Hex
import io.stepfunc.dnp3c.protocol.logging.Logger
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class PluginValidationTest extends AnyFunSuite with Matchers {

  // records the timing it's asked with and the points it's given; defers when allowed, as a manual plugin would
  private class Plugin extends DefaultIntegrationPlugin {
    val calls = mutable.ListBuffer.empty[(String, CheckTiming, List[Int])]

    private def record(name: String, reporter: CheckReporter, points: Seq[GenIndexedPoint[?]]*) = {
      val indexes = points.flatten.map(_.idx).toList
      calls += ((name, reporter.timing, indexes))
      if (reporter.timing == CheckTiming.Deferrable) Some(DeferredCheck(name, indexes.map(_.toString), () => ()))
      else None
    }

    override def verifyTimestamp(reporter: CheckReporter, points: Seq[GenIndexedPoint[MeasurementPoint]]) =
      record("timestamp", reporter, points)
    override def verifyClassEvents(
        reporter: CheckReporter,
        eventClass: EventClass,
        expectation: EventExpectation,
        points: Seq[GenIndexedPoint[MeasurementPoint]]) = record("class events", reporter, points)
    override def verifyAllStaticCurrentValues(
        reporter: CheckReporter,
        isClass0: Boolean,
        points: Seq[GenIndexedPoint[MeasurementPoint]]) = record("current status", reporter, points)
    override def verifyResponseRetainsEvents(
        reporter: CheckReporter,
        previous: Seq[GenIndexedPoint[MeasurementPoint]],
        current: Seq[GenIndexedPoint[MeasurementPoint]]) = record("same events", reporter, previous, current)
    override def checkAnalogOutputTolerance(reporter: CheckReporter, expected: Double, actual: Double) = {
      calls += (("tolerance", reporter.timing, List(actual.toInt)))
      if (reporter.timing == CheckTiming.Deferrable) Some(DeferredCheck("tolerance", Nil, () => ())) else None
    }
  }

  private val plugin = new Plugin
  private val reporter = new ProcedureReporter(Logger.empty, plugin)

  // a response with one binary input event at each index
  private def events(indexes: Int*): Message = {
    val headers = indexes.toList.flatMap(i => Message.parseObjectHeaders(Hex.from(f"02 01 17 01 $i%02X 81")))
    Message(Control.request(Sequence.zero), AppFunction.response, Some(IIN.empty), headers)
  }

  test("a plugin validation is immediate unless marked deferrable") {
    PluginValidations.verifyTimestamp().validate(reporter, events(1)) should equal(Nil)
    PluginValidations.verifyTimestamp().deferrable.validate(reporter, events(1)).map(_.description) should equal(
      List("timestamp"))
    plugin.calls.map(_._2).toList should equal(List(CheckTiming.Immediate, CheckTiming.Deferrable))
  }

  test("combined and multi-fragment validations keep every deferred check, in order") {
    val combined = PluginValidations.verifyTimestamp().deferrable &&
      PluginValidations.verifyOldAndNewClassEvents(EventClass.Class1).deferrable
    combined.validate(reporter, events(1)).map(_.description) should equal(List("timestamp", "class events"))

    val perFragment = PluginValidations.verifyTimestamp().deferrable.toResponseValidation
    perFragment.validate(reporter, List(events(1), events(2))).map(_.evidence) should equal(List(List("1"), List("2")))

    val whole = PluginValidations.verifyTimestamp().deferrable.acrossFragments
    whole.validate(reporter, List(events(1), events(2))).map(_.evidence) should equal(List(List("1", "2")))
  }

  test("a validation that calls the plugin more than once keeps every result") {
    // two analog output status values for index 3
    val headers = Message.parseObjectHeaders(Hex.from("28 02 00 03 03 01 0A 00 28 02 00 03 03 01 0B 00"))
    val response = Message(Control.request(Sequence.zero), AppFunction.response, Some(IIN.empty), headers)
    PluginValidations.checkAnalogOutputTolerance(3, 10).deferrable.validate(reporter, response) should have size 2
    plugin.calls.filter(_._1 == "tolerance").map(_._3).toList should equal(List(List(10), List(11)))
  }

  test("a deferred comparison is given the earlier response as it was when the step ran") {
    val recorder = new MessageRecorder
    recorder.record.validate(reporter, events(1))
    val step = PluginValidations.verifyResponseRetainsEvents(recorder).deferrable && recorder.record

    step.validate(reporter, events(2)).map(_.evidence) should equal(List(List("1", "2")))
    // the recorder has moved on to the new response
    recorder.previousMessage.map(_.iterate.map(_.idx).toList) should equal(Some(List(2)))
  }
}
