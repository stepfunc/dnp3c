// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.helpers

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.Placements.id
import io.stepfunc.dnp3c.conformance.api.{ BooleanOption, Options, ProcedureDefinition }
import io.stepfunc.dnp3c.conformance.s8.{ s814, s822 }
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
      1 -> s814.Procedure81421,
      9 -> s814.Procedure81429,
      10 -> s814.Procedure814210,
      12 -> s814.Procedure814212,
      13 -> s814.Procedure814213,
      15 -> s814.Procedure814215)
  )

  private val doubleBit = Family(
    InputType.DoubleBitBinaryInput,
    Map(
      1 -> s822.Procedure82221,
      9 -> s822.Procedure82229,
      10 -> s822.Procedure822210,
      12 -> s822.Procedure822212,
      13 -> s822.Procedure822213,
      15 -> s822.Procedure822215)
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
