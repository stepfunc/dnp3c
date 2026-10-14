// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import scala.util.Try

import io.stepfunc.dnp3c.conformance.Revisions
import io.stepfunc.dnp3c.conformance.api.{ ConfigurationException, Options }
import io.stepfunc.dnp3c.conformance.api.options.{ BinaryInputOptions, OutstationOptions, TimeSyncOptions }
import io.stepfunc.dnp3c.conformance.s8.helpers.InputType.BinaryInput
import io.stepfunc.dnp3c.conformance.v31r1.EventRules.Clock
import io.stepfunc.dnp3c.dnp3.StubDriver
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
      TimeSyncOptions.binaryInputChangeWithTime.id -> withTime.toString,
      TimeSyncOptions.binaryInputChangeWithRelativeTime.id -> relativeTime.toString
    ))

  private def passes(message: Message, read: io.stepfunc.dnp3c.dnp3.app.Variation, o: Options, clock: Clock): Boolean =
    Try(EventRules.consistent(BinaryInput, read, o, clock).validate(StubDriver.reporter(), message)).isSuccess

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
      io.stepfunc.dnp3c.conformance.api.options.DoubleBitInputOptions.defaultEventVariation.id -> "1")
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

  test("only a subset level 1 device may lack reads of its events") {
    def withoutReads(level: String) = Options.from(Map(
      OutstationOptions.subsetLevel.id -> level,
      io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs.binary.eventReads.id -> "false",
      BinaryInputOptions.defaultEventVariation.id -> "1",
      io.stepfunc.dnp3c.conformance.api.options.DoubleBitInputOptions.defaultEventVariation.id -> "1"
    ))
    Revisions.v3_1r1.validate(withoutReads("1")) should equal(1)
    an[ConfigurationException] should be thrownBy Revisions.v3_1r1.validate(withoutReads("2"))
  }
}
