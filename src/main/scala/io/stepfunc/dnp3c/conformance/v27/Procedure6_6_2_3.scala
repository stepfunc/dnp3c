// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.common.LinkSteps._
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.{ LinkOverride, Requests, TestDriver }
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE
import io.stepfunc.dnp3c.dnp3.DeferredCheck

object Procedure6_6_2_3 extends ProcedureDefinition {

  def readWrongAddress(offset: Int): TestStep = new TestStep {

    def description = List(s"Send a READ request to the configured link address + $offset")

    // the configured address is only known when the step runs
    def run(driver: TestDriver): List[DeferredCheck] = {
      val destination = UInt16LE(driver.remote.value + offset)
      driver.writeMessage(Requests.readClass123(Sequence.zero), LinkOverride.destination(destination))
      Nil
    }
  }

  override def steps(options: OptionSource): Iterable[LabeledStep] = {

    def steps(offset: Int) = List(
      1 --> readWrongAddress(offset),
      (2 to 3) --> expectLinkTimeout(Some(PrimaryFrames.severalSecondsMs))
    )
    val step4 = 4 --> describe("Run the checks above again with another destination address")

    PrimaryFrames.prologue ++ steps(offset = 1) ++ List(step4) ++ steps(offset = 2)
  }

}
