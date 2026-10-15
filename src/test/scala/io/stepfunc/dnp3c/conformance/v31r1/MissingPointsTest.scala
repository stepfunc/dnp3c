// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.api.{ BooleanOption, Options, ProcedureDefinition }
import io.stepfunc.dnp3c.conformance.api.options.OutstationOptions
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.{ Inputs, Outputs }
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.IIN
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// 3.1r1's tests of a point type the DUT doesn't have all want the same answer: a Null Error Response with IIN2.1 or
// IIN2.2, and IIN2.0 clear
@RunWith(classOf[JUnitRunner])
class MissingPointsTest extends AnyFunSuite with Matchers {

  // answers every read with nothing but the IIN bits
  private def passes(procedure: ProcedureDefinition, options: Options, iin: IIN): Boolean = {
    val dut = ScriptedDut.iinOnly(StubDriver.reporter(new DefaultIntegrationPlugin), iin)
    Run.passes(procedure, dut, options)
  }

  private def without(options: BooleanOption*): Options =
    Options.from(options.map(_.id -> "false").toMap + (OutstationOptions.subsetLevel.id -> "3"))

  private val noBinaryOutputs = without(Outputs.binary.statusPoints, Outputs.binary.commands)
  private val noAnalogOutputs = without(Outputs.analog.statusPoints, Outputs.analog.commands)

  // each test, with a configuration in which the DUT has none of the points it reads
  private val tests: List[(String, ProcedureDefinition, Options)] = List(
    ("8.1.2", Definitions.binaryOutputStatus, noBinaryOutputs),
    ("8.3.2", Definitions.analogOutputStatus, noAnalogOutputs),
    ("8.13.2b", StaticInputs.binaryWithout, without(Inputs.binary.points)),
    ("8.14.2b", InputEvents.binaryWithoutEvents, without(Inputs.binary.points)),
    ("8.16.1.2.2", StaticInputs.counterWithout, without(Inputs.counter.points)),
    ("8.16.2.2.2", StaticInputs.frozenCounterWithout, without(Inputs.frozenCounter.points)),
    ("8.17.2b", CounterAnalogEvents.counterWithout, without(Inputs.counter.points)),
    ("8.18.2b", StaticInputs.analogWithout, without(Inputs.analog.points)),
    ("8.19.2b", CounterAnalogEvents.analogChange, without(Inputs.analog.points)),
    ("8.21.1", DisableDoubleBit.static, without(Inputs.doubleBit.points)),
    ("8.21b", StaticInputs.doubleBitWithout, without(Inputs.doubleBit.points)),
    ("8.22.1", DisableDoubleBit.events, without(Inputs.doubleBit.points)),
    ("8.22b", InputEvents.doubleBitWithoutEvents, without(Inputs.doubleBit.points)),
    ("8.23.2.1.1", PointRanges.withoutPoints(PointRanges.binaryInputs), without(Inputs.binary.points)),
    ("8.23.2.6.1", PointRanges.withoutPoints(PointRanges.analogOutputStatus), noAnalogOutputs),
    ("8.24.2a", StaticVariations.test(StaticVariations.binaryInputs), without(Inputs.binary.points)),
    ("8.24.2b", StaticVariations.test(StaticVariations.doubleBitInputs), without(Inputs.doubleBit.points)),
    ("8.24.2c", StaticVariations.test(StaticVariations.binaryOutputStatus), noBinaryOutputs),
    ("8.24.2d", StaticVariations.test(StaticVariations.counters), without(Inputs.counter.points)),
    ("8.24.2e", StaticVariations.test(StaticVariations.frozenCounters), without(Inputs.frozenCounter.points)),
    ("8.24.2f", StaticVariations.test(StaticVariations.analogInputs), without(Inputs.analog.points)),
    ("8.24.2g", StaticVariations.test(StaticVariations.analogOutputStatus), noAnalogOutputs),
    ("8.25.2c", EventVariations.test(EventVariations.counters), without(Inputs.counter.points)),
    ("8.25.2d", EventVariations.test(EventVariations.frozenCounters), without(Inputs.frozenCounter.points)),
    ("8.25.2e", EventVariations.test(EventVariations.analogInputs), without(Inputs.analog.points))
  )

  test("a read of a type the DUT doesn't have gets IIN2.1 or IIN2.2, not a Null Response, and not IIN2.0") {
    for ((id, procedure, options) <- tests) withClue(s"$id: ") {
      procedure.disabledBy(options) should equal(None)
      passes(procedure, options, IIN.OBJECT_UNKNOWN) should equal(true)
      passes(procedure, options, IIN.PARAM_ERROR) should equal(true)
      passes(procedure, options, IIN.empty) should equal(false)
      passes(procedure, options, IIN.OBJECT_UNKNOWN | IIN.FUNC_NOT_SUPPORTED) should equal(false)
    }
  }

  test("a type the DUT has without events still answers a read of the events with a Null Response") {
    for ((id, procedure, options) <- List(
        ("8.14.2b", InputEvents.binaryWithoutEvents, without(Inputs.binary.events)),
        ("8.17.2b", CounterAnalogEvents.counterWithout, without(Inputs.counter.events)),
        ("8.19.2b", CounterAnalogEvents.analogChange, without(Inputs.analog.events)),
        ("8.22b", InputEvents.doubleBitWithoutEvents, without(Inputs.doubleBit.events))
      )) withClue(s"$id: ") {
      passes(procedure, options, IIN.empty) should equal(true)
      passes(procedure, options, IIN.OBJECT_UNKNOWN) should equal(false)
    }
  }

  test("a level 1 test of a type the DUT can't read runs only for a type it has; without the type, its own test runs") {
    val noPointsNoReads = without(Inputs.binary.points, Inputs.binary.reads)
    StaticInputs.binaryLevelOne.disabledBy(noPointsNoReads) should not equal None
    StaticInputs.binaryWithout.disabledBy(noPointsNoReads) should equal(None)
    StaticInputs.binaryLevelOne.disabledBy(without(Inputs.binary.reads)) should equal(None)
  }
}
