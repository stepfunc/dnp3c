// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import java.io.StringReader

import scala.util.Try

import io.stepfunc.dnp3c.conformance.api.{ Options, ProcedureDefinition }
import io.stepfunc.dnp3c.conformance.api.options.{ OutstationOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.s8.s81.Procedure812
import io.stepfunc.dnp3c.conformance.s8.s83.Procedure832
import io.stepfunc.dnp3c.dnp3.{ ScriptedDut, StubDriver }
import io.stepfunc.dnp3c.dnp3.app.{ AppFunction, IIN, Message, ObjectHeader }
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// 8.1.2 and 8.3.2 as 3.1r1 changes them, against an outstation that answers each read the same way
@RunWith(classOf[JUnitRunner])
class OutputStatusTest extends AnyFunSuite with Matchers {

  private def options(settings: (String, String)*): Options = Options.from(settings.toMap)

  // runs the steps, stopping at the first that fails; an operate is echoed, and a read gets the answer
  private def passes(procedure: ProcedureDefinition, options: Options, iin: IIN, objects: String = ""): Boolean = {
    def answer(request: Message): (IIN, List[ObjectHeader]) = request.function match {
      case AppFunction.read =>
        (iin, if (objects.isEmpty) Nil else Message.parse(Hex.from("C0 81 00 00 " + objects)).headers)
      case _ => (IIN.empty, request.headers)
    }
    val dut = new ScriptedDut(StubDriver.reporter(), answer)
    Console.withIn(new StringReader(""))(
      Try(procedure.configure(options).steps.foreach(s => s.step.run(dut).foreach(_.check()))).isSuccess)
  }

  private val noBinaryOutputs = options(SupportOptions.Outputs.binary.statusPoints.id -> "false")
  private val noAnalogOutputs = options(
    SupportOptions.Outputs.analog.statusPoints.id -> "false",
    SupportOptions.Outputs.analog.commands.id -> "false")

  test("8.1.2: without binary outputs, 3.1r1 needs a Null Error Response with IIN2.1 or IIN2.2; 2.7 a Null Response") {
    passes(Definitions.binaryOutputStatus, noBinaryOutputs, IIN.empty) should equal(false)
    passes(Definitions.binaryOutputStatus, noBinaryOutputs, IIN.OBJECT_UNKNOWN) should equal(true)
    passes(Definitions.binaryOutputStatus, noBinaryOutputs, IIN.PARAM_ERROR) should equal(true)
    passes(Definitions.binaryOutputStatus, noBinaryOutputs, IIN.FUNC_NOT_SUPPORTED) should equal(false)
    passes(Procedure812, noBinaryOutputs, IIN.empty) should equal(true)
  }

  test("8.3.2: without analog outputs, 3.1r1 takes a Null Response or a Null Error Response, but no objects") {
    passes(Definitions.analogOutputStatus, noAnalogOutputs, IIN.empty) should equal(true)
    passes(Definitions.analogOutputStatus, noAnalogOutputs, IIN.OBJECT_UNKNOWN) should equal(true)
    // an error response with objects: 2.7 took it, 3.1r1 doesn't
    val objects = "28 02 00 00 00 01 0A 00" // g40v2, index 0
    passes(Definitions.analogOutputStatus, noAnalogOutputs, IIN.OBJECT_UNKNOWN, objects) should equal(false)
    passes(Procedure832, noAnalogOutputs, IIN.OBJECT_UNKNOWN, objects) should equal(true)
  }

  test("8.3.2: 3.1r1 allows g40v1 at level 3 only") {
    val analogStatusOnly = (level: String) =>
      options(SupportOptions.Outputs.analog.commands.id -> "false", OutstationOptions.subsetLevel.id -> level)
    val g40v1 = "28 01 00 00 00 01 0A 00 00 00" // g40v1, index 0
    passes(Definitions.analogOutputStatus, analogStatusOnly("3"), IIN.empty, g40v1) should equal(true)
    passes(Definitions.analogOutputStatus, analogStatusOnly("2"), IIN.empty, g40v1) should equal(false)
  }
}
