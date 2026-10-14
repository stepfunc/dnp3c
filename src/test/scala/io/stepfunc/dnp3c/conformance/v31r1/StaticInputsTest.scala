// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import java.io.StringReader

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.api.{ Options, ProcedureDefinition }
import io.stepfunc.dnp3c.conformance.api.options.{ OutstationOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.conformance.v27
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.{ IIN, Message, ObjectHeader }
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class StaticInputsTest extends AnyFunSuite with Matchers {

  private class Plugin extends DefaultIntegrationPlugin {
    override def uninstall(reporter: PluginReporter, kind: PointKind): Unit = ()
    override def reinstallPoints(reporter: PluginReporter): Unit = ()
  }

  // answers every read with nothing but the IIN bits
  private def passes(procedure: ProcedureDefinition, options: Options, iin: IIN): Boolean = {
    val dut = new ScriptedDut(StubDriver.reporter(new Plugin), (_: Message) => (iin, List.empty[ObjectHeader]))
    Console.withIn(new StringReader(""))(
      Run.passes(procedure, dut, options))
  }

  test("8.13.2-2: a device without binary inputs answers with IIN2.1 or IIN2.2, not a Null Response") {
    val none = Options.from(Map(Inputs.binary.points.id -> "false"))
    passes(StaticInputs.binaryWithout, none, IIN.OBJECT_UNKNOWN) should equal(true)
    passes(StaticInputs.binaryWithout, none, IIN.PARAM_ERROR) should equal(true)
    passes(StaticInputs.binaryWithout, none, IIN.empty) should equal(false)
  }

  test("8.13.2-4: with the binary inputs uninstalled, a Null Error Response with IIN2.1 or IIN2.2") {
    passes(StaticInputs.binaryConfigurable, Options.from(Map()), IIN.OBJECT_UNKNOWN) should equal(true)
    passes(StaticInputs.binaryConfigurable, Options.from(Map()), IIN.empty) should equal(false)
  }

  test("8.13.2-1 runs for a level 1 device that can't read binary inputs, and 8.13.2-3 doesn't") {
    val noReads = Options.from(Map(Inputs.binary.reads.id -> "false"))
    StaticInputs.binaryLevelOne.disabledBy(noReads) should equal(None)
    StaticInputs.binaryHas.disabledBy(noReads) should not equal None
    StaticInputs.binaryLevelOne.disabledBy(Options.from(Map())) should not equal None
    StaticInputs.binaryHas.disabledBy(Options.from(Map())) should equal(None)
    passes(StaticInputs.binaryLevelOne, noReads, IIN.FUNC_NOT_SUPPORTED) should equal(true)
  }

  test("8.16 and 8.18: a device without the points answers with a Null Error Response, IIN2.1 or IIN2.2") {
    for ((procedure, input) <- List(
        StaticInputs.counterWithout -> Inputs.counter,
        StaticInputs.frozenCounterWithout -> Inputs.frozenCounter,
        StaticInputs.analogWithout -> Inputs.analog)) {
      val none = Options.from(Map(input.points.id -> "false"))
      procedure.disabledBy(none) should equal(None)
      passes(procedure, none, IIN.OBJECT_UNKNOWN) should equal(true)
      passes(procedure, none, IIN.PARAM_ERROR) should equal(true)
      passes(procedure, none, IIN.empty) should equal(false)
    }
  }

  test("8.16.1.2.4: with the counters uninstalled, a Null Error Response with IIN2.1 or IIN2.2") {
    passes(StaticInputs.counterConfigurable, Options.from(Map()), IIN.PARAM_ERROR) should equal(true)
    passes(StaticInputs.counterConfigurable, Options.from(Map()), IIN.empty) should equal(false)
  }

  test("8.16.1.2.1 reads g20v0 and g20v1, each answered with an error, and replaces 8.16.1.2.3") {
    val noReads = Options.from(Map(Inputs.counter.reads.id -> "false"))
    StaticInputs.counterLevelOne.disabledBy(noReads) should equal(None)
    StaticInputs.counterHas.disabledBy(noReads) should not equal None
    StaticInputs.counterLevelOne.configure(noReads).steps.map(_.label.stripPrefix("step: ")).tail should equal(
      List("1", "2", "3", "4", "5", "6"))
    passes(StaticInputs.counterLevelOne, noReads, IIN.FUNC_NOT_SUPPORTED) should equal(true)
    passes(StaticInputs.counterLevelOne, noReads, IIN.empty) should equal(false)
  }

  test("8.20.2-1 runs where the DUT can read none of its input types on their own, and 8.20.2-2 elsewhere") {
    val inputs = List(Inputs.binary, Inputs.doubleBit, Inputs.counter, Inputs.frozenCounter, Inputs.analog)
    // and no output statuses, which can always be read
    val outputs = List(SupportOptions.Outputs.binary.statusPoints, SupportOptions.Outputs.analog.statusPoints)
    val noReads = Options.from(
      (inputs.flatMap(i => List(i.reads.id -> "false", i.eventReads.id -> "false")) ++
        outputs.map(_.id -> "false")).toMap)
    val someReads = Options.from(Map(Inputs.binary.reads.id -> "false"))
    MultipleReads.levelOne.disabledBy(noReads) should equal(None)
    MultipleReads.multipleObjectTypes.disabledBy(noReads) should not equal None
    MultipleReads.levelOne.disabledBy(someReads) should not equal None
    MultipleReads.multipleObjectTypes.disabledBy(someReads) should equal(None)
    // a device whose only readable types are output statuses
    val outputsOnly = Options.from(
      inputs.map(_.points.id -> "false").toMap + (SupportOptions.Outputs.binary.statusPoints.id -> "true"))
    MultipleReads.levelOne.disabledBy(outputsOnly) should not equal None
    MultipleReads.multipleObjectTypes.disabledBy(outputsOnly) should equal(None)
    passes(MultipleReads.levelOne, noReads, IIN.OBJECT_UNKNOWN) should equal(true)
    passes(MultipleReads.levelOne, noReads, IIN.empty) should equal(false)
  }

  test("8.20.2-2 asks only that events come before static objects, and requests only what the DUT can read") {
    def describes(procedure: ProcedureDefinition, options: Options): String =
      procedure.configure(options).steps.flatMap(_.step.description).mkString("\n")
    val level2 = Options.from(Map(OutstationOptions.subsetLevel.id -> "2"))
    describes(v27.Procedure8_20_2, level2) should include("order of the request")
    describes(MultipleReads.multipleObjectTypes, level2) should not include "order of the request"
    describes(MultipleReads.multipleObjectTypes, level2) should include("g20v0")
    val noCounterReads = Options.from(Map(Inputs.counter.reads.id -> "false"))
    describes(MultipleReads.multipleObjectTypes, noCounterReads) should not include "g20v0"
    // the plugin's check expects every static point, which a request without the counters doesn't get
    describes(MultipleReads.multipleObjectTypes, level2) should not include "aren't checked against the plugin"
    describes(MultipleReads.multipleObjectTypes, noCounterReads) should include("aren't checked against the plugin")
  }
}
