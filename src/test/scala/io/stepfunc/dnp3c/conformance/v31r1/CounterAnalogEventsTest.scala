// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.s8
import io.stepfunc.dnp3c.conformance.api.{ LabeledStep, Options, ProcedureDefinition }
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// 3.1r1's 8.17 and 8.19: 2.7's steps, numbered as 3.1r1 numbers them, and run only where the events can be read
@RunWith(classOf[JUnitRunner])
class CounterAnalogEventsTest extends AnyFunSuite with Matchers {

  private def steps(procedure: ProcedureDefinition, options: Options = Options.from(Map())): List[LabeledStep] =
    procedure.configure(options).steps.toList

  private def lastStep(procedure: ProcedureDefinition): String = steps(procedure).last.label.stripPrefix("step: ")

  private def describes(procedure: ProcedureDefinition, label: String, options: Options = Options.from(Map())): String =
    steps(procedure, options).filter(_.label.endsWith(label)).flatMap(_.step.description).mkString("\n")

  test("the confirmations 3.1r1 numbers move the later steps on") {
    lastStep(s8.s817.Procedure81723) should equal("15")
    lastStep(CounterAnalogEvents.counterOneByteCount) should equal("17")
    lastStep(CounterAnalogEvents.counterTwoByteCount) should equal("17")
    lastStep(s8.s819.Procedure81921) should equal("14")
    lastStep(CounterAnalogEvents.analogChange) should equal("15")
  }

  test("8.17.2-3 leaves the first response with the events unconfirmed, which 2.7 confirms") {
    lastStep(s8.s817.Procedure81722) should equal("16")
    lastStep(CounterAnalogEvents.counterAll) should equal("17")
    describes(s8.s817.Procedure81722, "8 to 12") should include("Confirm each response")
    describes(CounterAnalogEvents.counterAll, "8 to 13") should not include "Confirm each response"
    describes(CounterAnalogEvents.counterAll, "17") should
      include("recorded at step 14 plus the increments made at step 15")
  }

  test("a level 1 device that can't read the events gets the level 1 test, and no other") {
    for ((input, levelOne, others) <- List(
        (
          Inputs.counter,
          CounterAnalogEvents.counterLevelOne,
          List(
            CounterAnalogEvents.counterWithout,
            CounterAnalogEvents.counterAll,
            CounterAnalogEvents.counterOneByteCount,
            CounterAnalogEvents.counterTwoByteCount,
            CounterAnalogEvents.counterWithoutConfirm
          )),
        (
          Inputs.analog,
          CounterAnalogEvents.analogLevelOne,
          List(CounterAnalogEvents.analogChange, CounterAnalogEvents.analogWithoutConfirm))
      )) {
      val noReads = Options.from(Map(input.eventReads.id -> "false"))
      levelOne.disabledBy(noReads) should equal(None)
      levelOne.disabledBy(Options.from(Map())) should not equal None
      others.foreach(_.disabledBy(noReads) should not equal None)
    }
    val noCounterEventReads = Options.from(Map(Inputs.counter.eventReads.id -> "false"))
    describes(CounterAnalogEvents.counterLevelOne, "3", noCounterEventReads) should include(
      "No further Counter event tests")
  }
}
