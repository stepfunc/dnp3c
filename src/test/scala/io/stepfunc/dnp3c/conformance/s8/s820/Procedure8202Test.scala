// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s820

import io.stepfunc.dnp3c.conformance.api.Options
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.{ Inputs, Outputs }
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects._
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class Procedure8202Test extends AnyFunSuite with Matchers {

  // the objects 8.20.2 requests in step 1
  private def requested(settings: (String, String)*): List[Variation] = {
    var sent = List.empty[Message]
    val driver = new StubDriver(StubDriver.reporter()) {
      override def writeMessage(message: Message, options: LinkOverride): Unit = sent = sent :+ message
    }
    Procedure8202.configure(Options.from(settings.toMap)).steps.head.step.run(driver)
    sent.flatMap(_.headers.map(_.variation))
  }

  test("requests every supported event type before every supported static type") {
    requested() should equal(List(
      Group2Var0,
      Group4Var0,
      Group22Var0,
      Group23Var0,
      Group32Var0,
      Group1Var0,
      Group3Var0,
      Group20Var0,
      Group21Var0,
      Group30Var0,
      Group10Var0,
      Group40Var0
    ))
  }

  test("requests output events from a DUT that reports them") {
    val withOutputEvents = requested(Outputs.binary.events.id -> "true", Outputs.analog.events.id -> "true")
    withOutputEvents should contain allOf (Group11Var0, Group42Var0)
  }

  test("leaves out the objects of a point type the DUT lacks, and events it doesn't report") {
    val withoutBinary = requested(Inputs.binary.points.id -> "false")
    withoutBinary should not contain Group1Var0
    withoutBinary should not contain Group2Var0

    val withoutBinaryEvents = requested(Inputs.binary.events.id -> "false")
    withoutBinaryEvents should contain(Group1Var0)
    withoutBinaryEvents should not contain Group2Var0
  }

  test("static objects must come in the order requested; events may come anywhere before them") {
    val order = MultipleObjectTypes.verifyStaticOrder(List(AllObjectsHeader(Group1Var0), AllObjectsHeader(Group20Var0)))
    def passes(objects: String) = scala.util.Try(
      order.validate(StubDriver.reporter(), Message.parse(Hex.from("C0 81 00 00 " + objects)))).isSuccess

    val binary = "01 02 00 00 00 01" // g1v2, index 0
    val counter = "14 01 00 00 00 01 2A 00 00 00" // g20v1, index 0
    val event = "02 01 17 01 05 81" // g2v1
    passes(s"$event $binary $counter") should equal(true)
    passes(s"$binary $counter") should equal(true)
    passes(s"$counter $binary") should equal(false)
  }

  test("only objects of the groups requested, and CTOs for events, each with its type's qualifier") {
    def passes(requested: List[Variation], objects: String) = scala.util.Try(
      io.stepfunc.dnp3c.conformance.s8.helpers.Validations
        .verifyOnlyRequestedObjects(requested.map(AllObjectsHeader.apply))
        .validate(StubDriver.reporter(), Message.parse(Hex.from("C0 81 00 00 " + objects)))).isSuccess

    val eventsAndBinary = List(Group2Var0, Group1Var0)
    val binary = "01 02 00 00 00 01" // g1v2, index 0
    val event = "02 01 17 01 05 81" // g2v1
    val ctoAndRelative = "33 01 07 01 00 00 00 00 00 00 02 03 17 01 05 81 00 00" // g51v1, then g2v3
    passes(eventsAndBinary, s"$event $binary") should equal(true)
    passes(eventsAndBinary, ctoAndRelative) should equal(true)
    passes(eventsAndBinary, "") should equal(true) // no events, no static data: other checks judge that
    // objects of a group not requested
    passes(eventsAndBinary, "2B 02 17 01 00 00 00 00") should equal(false) // g43v2
    passes(eventsAndBinary, "14 01 00 00 00 01 2A 00 00 00") should equal(false) // g20v1
    // a CTO, with no events requested
    passes(List(Group1Var0), s"33 01 07 01 00 00 00 00 00 00 $binary") should equal(false)
    // a static object with an event's qualifier, and the reverse
    passes(eventsAndBinary, "01 02 17 01 00 01") should equal(false)
    passes(eventsAndBinary, "02 01 00 05 05 81") should equal(false)
  }

  test("8.20.2 rejects a response with objects of a group it didn't request") {
    // a DUT that answers with the objects; the plugin accepts any values
    def passes(objects: String) = {
      val plugin = new DefaultIntegrationPlugin {
        override def verifyClassEvents(
            reporter: CheckReporter,
            eventClass: EventClass,
            expectation: EventExpectation,
            points: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck] = None
        override def verifyAllStaticCurrentValues(
            reporter: CheckReporter,
            isClass0: Boolean,
            points: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck] = None
      }
      val driver = new StubDriver(StubDriver.reporter(plugin)) {
        override def writeMessage(message: Message, options: LinkOverride): Unit = ()
        override def readMessage(
            unsolicited: Unsolicited,
            validator: FrameValidator,
            linkOverride: LinkOverride,
            timeoutMs: Option[Int]): Option[Message] = Some(Message.parse(Hex.from("E0 81 00 00 " + objects)))
      }
      val steps = Procedure8202.configure(Options.from(Map())).steps.toList
      scala.util.Try(steps.foreach(_.step.run(driver))).isSuccess
    }
    val binary = "01 02 00 00 00 01" // g1v2, index 0
    passes(binary) should equal(true)
    passes(s"2B 02 17 01 00 00 00 00 $binary") should equal(false) // g43v2 first
  }

  test("event objects must come before static ones, across the fragments of the response") {
    def passes(fragments: String*) = scala.util.Try(
      io.stepfunc.dnp3c.conformance.s8.helpers.Validations.verifyEventDataPrecedesStaticData.validate(
        StubDriver.reporter(),
        fragments.map(f => Message.parse(Hex.from("C0 81 00 00 " + f))).toList)).isSuccess

    val event = "02 01 17 01 05 81"
    val static = "01 02 00 00 00 01"
    passes(s"$event $static") should equal(true)
    passes(event, static) should equal(true)
    passes(s"$static $event") should equal(false)
    passes(static, event) should equal(false)
  }
}
