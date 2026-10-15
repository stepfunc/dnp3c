// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.common.LinkSteps._
import io.stepfunc.dnp3c.conformance.common.GenericSteps.expectNoAnswer
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.link.{ Control, LinkFunction }
import io.stepfunc.dnp3c.dnp3.{ LinkOverride, Requests, TestDriver }
import io.stepfunc.dnp3c.protocol.Hex
import io.stepfunc.dnp3c.dnp3.DeferredCheck

object Procedure6_6_2_2 extends ProcedureDefinition {

  def readClass0WithInvalidCode(function: Byte): TestStep = new TestStep {
    def description = List(
      "Build a class 0 read (g60v1, qualifier 0x06) in a confirmed user data frame (control 0xD3)",
      "Keep the DIR, PRM, FCB and FCV bits of the control field valid, but set the function code to the undefined value " +
        Hex.prefixed(function),
      "Recompute the header CRC so it matches the altered control field",
      "Send the request"
    )

    def run(driver: TestDriver): List[DeferredCheck] = {
      // 0xD3 (DIR, PRM, FCV set; FCB clear) with only the function code changed
      val control = Control.build(master = true, pri = true, fcb = false, fcvdfc = true, func = function)
      driver.writeMessage(Requests.readClass0(Sequence.zero), LinkOverride.control(control))
      Nil
    }
  }

  override def steps(options: OptionSource): Iterable[LabeledStep] = PrimaryFrames.prologue ++
    stepsWithInvalidCode(0x05, options) ++
    List(7 --> describe("Run the checks above again with another invalid function code")) ++
    stepsWithInvalidCode(0x0A, options)

  def stepsWithInvalidCode(invalid: Byte, options: OptionSource): Iterable[LabeledStep] = List(
    (1 to 3) --> readClass0WithInvalidCode(invalid),
    (4 to 5) --> expectLinkTimeoutOr(withFunction(LinkFunction.NOT_SUPPORTED), PrimaryFrames.severalSecondsMs),
    6 --> expectNoAnswer(options)
  )

}
