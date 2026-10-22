// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.Revisions
import io.stepfunc.dnp3c.conformance.api.{ ConfigurationException, Options }
import io.stepfunc.dnp3c.conformance.api.options.{ BinaryInputOptions, OutstationOptions, TimeSyncOptions }
import io.stepfunc.dnp3c.conformance.common.InputType.BinaryInput
import io.stepfunc.dnp3c.conformance.common.EventTime.Clock
import io.stepfunc.dnp3c.dnp3.Validate
import io.stepfunc.dnp3c.dnp3.app.Message
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group2Var0, Group2Var1, Group2Var2, Group2Var3 }
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class EventRulesTest extends AnyFunSuite with Matchers {

  // one binary input event at index 0, online, in each form
  private val withoutTime = "02 01 17 01 00 01"
  private val absoluteTime = "02 02 17 01 00 01 00 00 00 00 00 00"
  private val synchronizedCto = "33 01 07 01 00 00 00 00 00 00"
  private val unsynchronizedCto = "33 02 07 01 00 00 00 00 00 00"
  private val relativeTime = "02 03 17 01 00 01 00 00"

  private def response(objects: String*): Message = Message.parse(Hex.from("E0 81 00 00 " + objects.mkString(" ")))

  private def options(defaultVariation: Int, withTime: Boolean = true, relativeTime: Boolean = true): Options =
    Options.from(Map(
      BinaryInputOptions.defaultEventVariation.id -> defaultVariation.toString,
      TimeSyncOptions.binaryInput.withTime.id -> withTime.toString,
      TimeSyncOptions.binaryInput.withRelativeTime.id -> relativeTime.toString
    ))

  private def passes(message: Message, read: io.stepfunc.dnp3c.dnp3.app.Variation, o: Options, clock: Clock): Boolean =
    Validate.passes(EventRules.consistent(BinaryInput, read, o, clock), message)

  test("without time preferred: only g2v1") {
    passes(response(withoutTime), Group2Var1, options(2), Clock.Unknown) should equal(true)
    passes(response(absoluteTime), Group2Var1, options(2), Clock.Unknown) should equal(false)
  }

  test("a read of variation 0 prefers the Device Profile's default") {
    passes(response(withoutTime), Group2Var0, options(1), Clock.Unknown) should equal(true)
    passes(response(absoluteTime), Group2Var0, options(1), Clock.Unknown) should equal(false)
    passes(response(absoluteTime), Group2Var0, options(2), Clock.Synchronized) should equal(true)
  }

  test("with time preferred: synchronized events in the preferred variation, unsynchronized after g51v2 in g2v3") {
    passes(response(absoluteTime), Group2Var2, options(1), Clock.Synchronized) should equal(true)
    passes(response(unsynchronizedCto, relativeTime), Group2Var2, options(1), Clock.Unsynchronized) should equal(true)
    // what #6 described: a g2v2 read answered with relative time and an unsynchronized CTO
    passes(response(unsynchronizedCto, relativeTime), Group2Var2, options(1), Clock.Unknown) should equal(true)
    passes(response(unsynchronizedCto, relativeTime), Group2Var2, options(1), Clock.Synchronized) should equal(false)
    passes(response(absoluteTime), Group2Var2, options(1), Clock.Unsynchronized) should equal(false)
  }

  test("relative time preferred and synchronized: g2v3 after a synchronized CTO") {
    passes(response(synchronizedCto, relativeTime), Group2Var3, options(1), Clock.Synchronized) should equal(true)
    passes(response(unsynchronizedCto, relativeTime), Group2Var3, options(1), Clock.Synchronized) should equal(false)
  }

  test("a device without relative time can't report unsynchronized events, so an unknown clock means synchronized") {
    val absoluteOnly = options(1, relativeTime = false)
    passes(response(unsynchronizedCto, relativeTime), Group2Var2, absoluteOnly, Clock.Unknown) should equal(false)
    passes(response(absoluteTime), Group2Var2, absoluteOnly, Clock.Unknown) should equal(true)
  }

  test("a device whose events carry no time reports them without time, whatever is preferred") {
    val noTime = options(2, withTime = false, relativeTime = false)
    passes(response(withoutTime), Group2Var2, noTime, Clock.Unknown) should equal(true)
    passes(response(absoluteTime), Group2Var2, noTime, Clock.Unknown) should equal(false)
  }

  test("3.1r1 needs the default variation of each input whose events can be read; 2.7 doesn't") {
    val level = Map(OutstationOptions.subsetLevel.id -> "2")
    an[ConfigurationException] should be thrownBy Revisions.v3_1r1.validate(Options.from(level))
    Revisions.v2_7.validate(Options.from(level)) should equal(2)
    val set = level ++ Map(
      BinaryInputOptions.defaultEventVariation.id -> "1",
      io.stepfunc.dnp3c.conformance.api.options.DoubleBitInputOptions.defaultEventVariation.id -> "1",
      TimeSyncOptions.timeToUnsynchronizedMs.id -> "0",
      io.stepfunc.dnp3c.conformance.api.options.AnalogInputOptions.valueReturnDeadband.id -> "10",
      io.stepfunc.dnp3c.conformance.api.options.ClassOptions.class1.binaryInputs.id -> "0",
      io.stepfunc.dnp3c.conformance.api.options.ClassOptions.class2.binaryInputs.id -> "1, 2"
    )
    Revisions.v3_1r1.validate(Options.from(set)) should equal(2)
  }

  test("only a run that includes event tests needs the default variation") {
    val level = Options.from(Map(OutstationOptions.subsetLevel.id -> "2"))
    val linkLayer = Revisions.v3_1r1.elements("6").asInstanceOf[io.stepfunc.dnp3c.conformance.api.TestSection]
    def procedures(s: io.stepfunc.dnp3c.conformance.api.TestSection)
        : List[io.stepfunc.dnp3c.conformance.api.TestProcedure] =
      s.procedures.toList ++ s.sections.toList.flatMap(procedures)
    Revisions.v3_1r1.validate(level, procedures(linkLayer)) should equal(2)
  }

  test("8.14.2m needs the manufacturer's time to an unsynchronized clock, where its third phase runs") {
    val base = Map(
      OutstationOptions.subsetLevel.id -> "2",
      BinaryInputOptions.defaultEventVariation.id -> "1",
      io.stepfunc.dnp3c.conformance.api.options.DoubleBitInputOptions.defaultEventVariation.id -> "1"
    )
    val test =
      List(Revisions.v3_1r1.elements("8.14.2m").asInstanceOf[io.stepfunc.dnp3c.conformance.api.TestProcedure])
    an[ConfigurationException] should be thrownBy Revisions.v3_1r1.validate(Options.from(base), test)
    Revisions.v3_1r1.validate(
      Options.from(base ++ Map(
        TimeSyncOptions.timeToUnsynchronizedMs.id -> "0",
        io.stepfunc.dnp3c.conformance.api.options.AnalogInputOptions.valueReturnDeadband.id -> "10",
        io.stepfunc.dnp3c.conformance.api.options.ClassOptions.class1.binaryInputs.id -> "0",
        io.stepfunc.dnp3c.conformance.api.options.ClassOptions.class2.binaryInputs.id -> "1, 2"
      )),
      test
    ) should
      equal(2)
    // a device whose time the master can't write never asks for it, so there's no third phase
    Revisions.v3_1r1.validate(
      Options.from(base + (TimeSyncOptions.supportSynchronizingTimeFromMaster.id -> "false")),
      test) should equal(2)
  }

  test("only a subset level 1 device may lack reads of its events") {
    def withoutReads(level: String) = Options.from(Map(
      OutstationOptions.subsetLevel.id -> level,
      io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs.binary.eventReads.id -> "false",
      BinaryInputOptions.defaultEventVariation.id -> "1",
      io.stepfunc.dnp3c.conformance.api.options.DoubleBitInputOptions.defaultEventVariation.id -> "1",
      TimeSyncOptions.timeToUnsynchronizedMs.id -> "0",
      io.stepfunc.dnp3c.conformance.api.options.AnalogInputOptions.valueReturnDeadband.id -> "10",
      io.stepfunc.dnp3c.conformance.api.options.ClassOptions.class1.binaryInputs.id -> "0",
      io.stepfunc.dnp3c.conformance.api.options.ClassOptions.class2.binaryInputs.id -> "1, 2"
    ))
    Revisions.v3_1r1.validate(withoutReads("1")) should equal(1)
    an[ConfigurationException] should be thrownBy Revisions.v3_1r1.validate(withoutReads("2"))
  }

  test("only a subset level 1 device may lack reads of counter, frozen counter or analog input events") {
    import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
    for (input <- List(Inputs.counter, Inputs.frozenCounter, Inputs.analog)) {
      def withoutReads(level: String) = Options.from(Map(
        OutstationOptions.subsetLevel.id -> level,
        input.eventReads.id -> "false",
        BinaryInputOptions.defaultEventVariation.id -> "1",
        io.stepfunc.dnp3c.conformance.api.options.DoubleBitInputOptions.defaultEventVariation.id -> "1",
        TimeSyncOptions.timeToUnsynchronizedMs.id -> "0",
        io.stepfunc.dnp3c.conformance.api.options.AnalogInputOptions.valueReturnDeadband.id -> "10",
        io.stepfunc.dnp3c.conformance.api.options.ClassOptions.class1.binaryInputs.id -> "0",
        io.stepfunc.dnp3c.conformance.api.options.ClassOptions.class2.binaryInputs.id -> "1, 2"
      ))
      Revisions.v3_1r1.validate(withoutReads("1")) should equal(1)
      an[ConfigurationException] should be thrownBy Revisions.v3_1r1.validate(withoutReads("3"))
    }
  }

  test("a subset level 3 device with frozen counters must generate frozen counter events") {
    import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
    def withoutEvents(level: String) = Options.from(Map(
      OutstationOptions.subsetLevel.id -> level,
      Inputs.frozenCounter.events.id -> "false",
      BinaryInputOptions.defaultEventVariation.id -> "1",
      io.stepfunc.dnp3c.conformance.api.options.DoubleBitInputOptions.defaultEventVariation.id -> "1",
      TimeSyncOptions.timeToUnsynchronizedMs.id -> "0",
      io.stepfunc.dnp3c.conformance.api.options.AnalogInputOptions.valueReturnDeadband.id -> "10",
      io.stepfunc.dnp3c.conformance.api.options.ClassOptions.class1.binaryInputs.id -> "0",
      io.stepfunc.dnp3c.conformance.api.options.ClassOptions.class2.binaryInputs.id -> "1, 2"
    ))
    Revisions.v3_1r1.validate(withoutEvents("2")) should equal(2)
    an[ConfigurationException] should be thrownBy Revisions.v3_1r1.validate(withoutEvents("3"))
  }

  test("8.19.2.4 needs the deadband only where it runs, and a tolerance that tells its two values apart") {
    import io.stepfunc.dnp3c.conformance.api.options.AnalogInputOptions
    import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
    val base = Map(
      OutstationOptions.subsetLevel.id -> "2",
      BinaryInputOptions.defaultEventVariation.id -> "1",
      io.stepfunc.dnp3c.conformance.api.options.DoubleBitInputOptions.defaultEventVariation.id -> "1",
      TimeSyncOptions.timeToUnsynchronizedMs.id -> "0",
      io.stepfunc.dnp3c.conformance.api.options.ClassOptions.class1.binaryInputs.id -> "0",
      io.stepfunc.dnp3c.conformance.api.options.ClassOptions.class2.binaryInputs.id -> "1, 2"
    )
    def valid(settings: (String, String)*) =
      scala.util.Try(Revisions.v3_1r1.validate(Options.from(base ++ settings))).isSuccess
    valid() should equal(false)
    valid(Inputs.analog.events.id -> "false") should equal(true)
    valid(AnalogInputOptions.valueReturnDeadband.id -> "10") should equal(true)
    // a change of 15: values 7 away from each are still closer to it than to the other
    valid(AnalogInputOptions.valueReturnDeadband.id -> "10", AnalogInputOptions.valueTolerance.id -> "7") should
      equal(true)
    valid(AnalogInputOptions.valueReturnDeadband.id -> "10", AnalogInputOptions.valueTolerance.id -> "8") should
      equal(false)
  }
}
