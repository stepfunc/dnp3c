// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.common

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.api.{ Bulletin, Options, ProcedureDefinition }
import io.stepfunc.dnp3c.conformance.api.options.{ OutstationOptions, TimeSyncOptions }
import io.stepfunc.dnp3c.conformance.common.EventTime.{ Batches, Clock }
import io.stepfunc.dnp3c.conformance.{ v27, v31r1 }
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group2Var2, Group4Var2 }
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// the clock the plugin reports while events are generated, and how 2.7 and 3.1r1 check the events' variations by it
@RunWith(classOf[JUnitRunner])
class EventTimeTest extends AnyFunSuite with Matchers {

  import TimeQuality.{ Synchronized, Unsynchronized }

  // a plugin that reports the clock's time quality before and after each generation as given, in turn, and passes every
  // check of the events' values
  private class Plugin(qualities: Option[TimeQuality]*) extends DefaultIntegrationPlugin {
    private val answers = Iterator.continually(qualities).flatten
    override def timeQuality(reporter: PluginReporter): Option[TimeQuality] = answers.next()
    override def generateInputChanges(reporter: PluginReporter, kind: InputKind[?]): Unit = ()
    override def generateExactInputEvents(reporter: PluginReporter, kind: InputKind[?], numEvents: Int): Unit = ()
    override def verifyInputEvents[P <: MeasurementPoint](
        reporter: CheckReporter,
        kind: InputKind[P],
        expectation: EventExpectation,
        points: Seq[GenIndexedPoint[P]]): Option[DeferredCheck] = None
  }

  // a DUT that answers a read of events with absolute time with the objects, in hex, asking for confirmation, and any
  // other read with a Null Response
  private class Dut(reporter: ProcedureReporter, objects: String) extends RespondingDriver(reporter) {
    override def writeMessage(message: Message, options: LinkOverride): Unit =
      if (message.function != AppFunction.confirm) {
        val events = message.headers.exists(h => h.variation == Group2Var2 || h.variation == Group4Var2)
        val control = 0xC0 | (if (events) 0x20 else 0) | message.ctrl.seq.value
        enqueue(f"$control%02X 81 00 00 " + (if (events) objects else ""))
      }
  }

  // one event at index 0, online, in each form, for binary and double-bit inputs
  private case class Forms(absolute: String, relative: String)
  private val binary = Forms("02 02 17 01 00 01 00 00 00 00 00 00", "02 03 17 01 00 01 00 00")
  private val doubleBit = Forms("04 02 17 01 00 01 00 00 00 00 00 00", "04 03 17 01 00 01 00 00")
  private val synchronizedCto = "33 01 07 01 00 00 00 00 00 00"
  private val unsynchronizedCto = "33 02 07 01 00 00 00 00 00 00"

  private val tb2017003a = OutstationOptions.bulletins.id -> Bulletin.TB2017_003a.id

  private def passes(
      procedure: ProcedureDefinition,
      objects: String,
      plugin: Plugin,
      settings: (String, String)*): Boolean = {
    val reporter = StubDriver.reporter(plugin)
    Run.passes(procedure, new Dut(reporter, objects), Options.from(settings.toMap))
  }

  private val families = List(
    (binary, v27.Procedure8_14_2_9, v31r1.InputEvents.binaryAllWithTime),
    (doubleBit, v27.Procedure8_22_2_9, v31r1.InputEvents.doubleBitAllWithTime))

  test("the option lists the bulletins, by id in any case, and none by default") {
    Options.from(Map()).get(OutstationOptions.bulletins) should equal(Set.empty)
    Options.from(Map(OutstationOptions.bulletins.id -> " tb2017-003A ")).get(OutstationOptions.bulletins) should
      equal(Set(Bulletin.TB2017_003a))
    an[Exception] should be thrownBy Options.from(Map(OutstationOptions.bulletins.id -> "TB2017-003"))
  }

  test("the clock recorded for a batch: as the plugin reports it, changed if it changed, unknown if it didn't say") {
    def recorded(batches: Batches, clocks: (Option[TimeQuality], Option[TimeQuality])*): Clock = {
      val reporter = StubDriver.reporter()
      clocks.foreach((before, after) => reporter.recordEventClock(before, after))
      EventTime.recorded(reporter, batches)
    }
    recorded(Batches.Latest) should equal(Clock.Unknown)
    recorded(Batches.Latest, Some(Synchronized) -> Some(Synchronized)) should equal(Clock.Synchronized)
    recorded(Batches.Latest, Some(Unsynchronized) -> Some(Unsynchronized)) should equal(Clock.Unsynchronized)
    recorded(Batches.Latest, Some(Synchronized) -> Some(Unsynchronized)) should equal(Clock.Changed)
    recorded(Batches.Latest, Some(Synchronized) -> None) should equal(Clock.Unknown)
    val twoBatches = List(Some(Unsynchronized) -> Some(Unsynchronized), Some(Synchronized) -> Some(Synchronized))
    recorded(Batches.Latest, twoBatches*) should equal(Clock.Synchronized)
    recorded(Batches.All, twoBatches*) should equal(Clock.Changed)
  }

  test("2.7 without the bulletin accepts only absolute time, whatever the plugin reports") {
    for ((forms, procedure, _) <- families) {
      passes(procedure, forms.absolute, new Plugin(Some(Unsynchronized))) should equal(true)
      passes(procedure, unsynchronizedCto + " " + forms.relative, new Plugin(Some(Unsynchronized))) should equal(false)
    }
  }

  test("2.7 with TB2017-003a: absolute time for synchronized events, relative time after g51v2 for unsynchronized") {
    for ((forms, procedure, _) <- families) {
      val unsynchronized = unsynchronizedCto + " " + forms.relative
      passes(procedure, forms.absolute, new Plugin(Some(Synchronized)), tb2017003a) should equal(true)
      passes(procedure, unsynchronized, new Plugin(Some(Synchronized)), tb2017003a) should equal(false)
      passes(procedure, unsynchronized, new Plugin(Some(Unsynchronized)), tb2017003a) should equal(true)
      passes(procedure, forms.absolute, new Plugin(Some(Unsynchronized)), tb2017003a) should equal(false)
      // a synchronized event is reported in the variation requested, not with relative time after g51v1
      passes(procedure, synchronizedCto + " " + forms.relative, new Plugin(Some(Synchronized)), tb2017003a) should
        equal(false)
    }
  }

  test("2.7 with TB2017-003a, where the plugin doesn't know the clock: either way, throughout") {
    for ((forms, procedure, _) <- families) {
      passes(procedure, forms.absolute, new Plugin(None), tb2017003a) should equal(true)
      passes(procedure, unsynchronizedCto + " " + forms.relative, new Plugin(None), tb2017003a) should equal(true)
    }
  }

  test("2.7 with TB2017-003a notes the bulletin on the result, whether it passes or fails") {
    val reporter = StubDriver.reporter(new Plugin(Some(Synchronized)))
    Run.passes(v27.Procedure8_14_2_9, new Dut(reporter, binary.absolute), Options.from(Map(tb2017003a))) should
      equal(true)
    reporter.notes.head should include("TB2017-003a 5.1.3.1.2")
    // and before a check that fails, for its log
    val failing = StubDriver.reporter(new Plugin(Some(Unsynchronized)))
    Run.passes(v27.Procedure8_14_2_9, new Dut(failing, binary.absolute), Options.from(Map(tb2017003a))) should
      equal(false)
    failing.notes.head should include("TB2017-003a 5.1.3.1.2")
  }

  test("3.1r1 checks the events by the clock the plugin reports, with or without the bulletin") {
    for ((forms, _, procedure) <- families; bulletins <- List(Nil, List(tb2017003a))) {
      val unsynchronized = unsynchronizedCto + " " + forms.relative
      passes(procedure, forms.absolute, new Plugin(Some(Synchronized)), bulletins*) should equal(true)
      passes(procedure, forms.absolute, new Plugin(Some(Unsynchronized)), bulletins*) should equal(false)
      passes(procedure, unsynchronized, new Plugin(Some(Unsynchronized)), bulletins*) should equal(true)
      passes(procedure, unsynchronized, new Plugin(Some(Synchronized)), bulletins*) should equal(false)
      // unknown, as before: either way, throughout
      passes(procedure, forms.absolute, new Plugin(None), bulletins*) should equal(true)
      passes(procedure, unsynchronized, new Plugin(None), bulletins*) should equal(true)
    }
  }

  test("events recorded while the clock changed may be reported each way, in one response") {
    val mixed = s"${binary.absolute} $unsynchronizedCto ${binary.relative}"
    passes(v31r1.InputEvents.binaryAllWithTime, mixed, new Plugin(Some(Synchronized), Some(Unsynchronized))) should
      equal(true)
    passes(v27.Procedure8_14_2_9, mixed, new Plugin(Some(Synchronized), Some(Unsynchronized)), tb2017003a) should
      equal(true)
    // but not where the clock didn't change, or where the plugin doesn't know it did
    passes(v31r1.InputEvents.binaryAllWithTime, mixed, new Plugin(Some(Synchronized))) should equal(false)
    passes(v31r1.InputEvents.binaryAllWithTime, mixed, new Plugin(None)) should equal(false)
    // relative time after a synchronized CTO is still wrong for a read of absolute time
    val wrongCto = s"${binary.absolute} $synchronizedCto ${binary.relative}"
    passes(v31r1.InputEvents.binaryAllWithTime, wrongCto, new Plugin(Some(Synchronized), Some(Unsynchronized))) should
      equal(false)
  }

  test("a device without relative time reports events as synchronized where the clock is unknown, as before") {
    val absoluteOnly = TimeSyncOptions.binaryInput.withRelativeTime.id -> "false"
    passes(v27.Procedure8_14_2_9, binary.absolute, new Plugin(None), tb2017003a, absoluteOnly) should equal(true)
    passes(
      v27.Procedure8_14_2_9,
      s"$unsynchronizedCto ${binary.relative}",
      new Plugin(None),
      tb2017003a,
      absoluteOnly) should
      equal(false)
  }
}
