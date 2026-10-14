// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.common

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.Placements.id
import io.stepfunc.dnp3c.conformance.api.{ BooleanOption, Options, ProcedureDefinition }
import io.stepfunc.dnp3c.conformance.v27
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group2Var0, Group4Var0 }
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// the binary and double-bit input event procedures written once in InputEventProcedures
@RunWith(classOf[JUnitRunner])
class InputEventProceduresTest extends AnyFunSuite with Matchers {

  private case class Family(input: InputType, procedures: Int => ProcedureDefinition)

  private val binary = Family(
    InputType.BinaryInput,
    Map(
      1 -> v27.Procedure8_14_2_1,
      9 -> v27.Procedure8_14_2_9,
      10 -> v27.Procedure8_14_2_10,
      12 -> v27.Procedure8_14_2_12,
      13 -> v27.Procedure8_14_2_13,
      15 -> v27.Procedure8_14_2_15
    )
  )

  private val doubleBit = Family(
    InputType.DoubleBitBinaryInput,
    Map(
      1 -> v27.Procedure8_22_2_1,
      9 -> v27.Procedure8_22_2_9,
      10 -> v27.Procedure8_22_2_10,
      12 -> v27.Procedure8_22_2_12,
      13 -> v27.Procedure8_22_2_13,
      15 -> v27.Procedure8_22_2_15
    )
  )

  private def runs(procedure: ProcedureDefinition, settings: (BooleanOption, Boolean)*): Boolean =
    procedure.disabledBy(Options.from(settings.map((o, v) => o.id -> v.toString).toMap)).isEmpty

  test("a DUT without the input's events runs only the procedure for that") {
    for (Family(input, procedure) <- List(binary, doubleBit)) {
      runs(procedure(1), input.support.events -> false) should equal(true)
      runs(procedure(1), input.support.events -> true) should equal(false)
      runs(procedure(9), input.support.events -> false) should equal(false)
    }
  }

  test("the 0x06 time procedures run without time support, the others need it") {
    for (Family(input, procedure) <- List(binary, doubleBit)) {
      runs(procedure(9), input.withTime -> false) should equal(true)
      runs(procedure(10), input.withTime -> false) should equal(false)
      runs(procedure(10), input.withTime -> true) should equal(true)

      runs(procedure(12), input.withRelativeTime -> false) should equal(true)
      runs(procedure(13), input.withRelativeTime -> false) should equal(false)
      runs(procedure(15), input.withRelativeTime -> false) should equal(false)
      runs(procedure(15), input.withRelativeTime -> true) should equal(true)
    }
  }

  test("each input's time options gate only its own procedures") {
    runs(binary.procedures(10), doubleBit.input.withTime -> false) should equal(true)
    runs(doubleBit.procedures(10), binary.input.withTime -> false) should equal(true)
  }

  test("a DUT without the input's events is asked for that input's events, and must answer with a Null Response") {
    for ((family, variation) <- List(binary -> Group2Var0, doubleBit -> Group4Var0)) {
      val dut = new Dut(_ => (IIN.empty, Nil))
      Run.passing(family.procedures(1), dut, Map(family.input.support.events.id -> "false"))
      dut.requests.flatMap(_.headers.map(_.variation)) should equal(List(variation))

      val withEvents = new Dut(r => (IIN.empty, r.headers))
      an[Exception] should be thrownBy
        Run.passing(family.procedures(1), withEvents, Map(family.input.support.events.id -> "false"))
    }
  }

  private class Dut(answer: Message => (IIN, List[ObjectHeader])) extends ScriptedDut(StubDriver.reporter(), answer)
}
