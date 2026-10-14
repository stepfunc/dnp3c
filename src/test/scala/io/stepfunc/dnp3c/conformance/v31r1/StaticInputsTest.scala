// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import java.io.StringReader

import scala.util.Try

import io.stepfunc.dnp3c.conformance.api.{ Options, ProcedureDefinition }
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.{ IIN, Message, ObjectHeader }
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class StaticInputsTest extends AnyFunSuite with Matchers {

  private class Plugin extends DefaultIntegrationPlugin {
    override def uninstallBinaryInputs(reporter: PluginReporter): Unit = ()
    override def reinstallPoints(reporter: PluginReporter): Unit = ()
  }

  // answers every read with nothing but the IIN bits
  private def passes(procedure: ProcedureDefinition, options: Options, iin: IIN): Boolean = {
    val dut = new ScriptedDut(StubDriver.reporter(new Plugin), (_: Message) => (iin, List.empty[ObjectHeader]))
    Console.withIn(new StringReader(""))(
      Try(procedure.configure(options).steps.foreach(s => s.step.run(dut).foreach(_.check()))).isSuccess)
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
}
