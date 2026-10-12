// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s6.s66.s662

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s6._
import io.stepfunc.dnp3c.conformance.s6.s66.s662
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.{ LinkOverride, Requests, TestDriver }
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE
import io.stepfunc.dnp3c.dnp3.DeferredCheck

object Procedure6623 extends TestProcedure {

  def id = "6.6.2.3"

  def description = "Invalid Destination Address"

  def readWrongAddress(offset: Int): TestStep = new TestStep {

    def description = List(s"Send a READ request to the configured link address + $offset")

    // the configured address is only known when the step runs
    def run(driver: TestDriver): List[DeferredCheck] = {
      val destination = UInt16LE(driver.remote.value + offset)
      driver.writeMessage(Requests.readClass123(Sequence.zero), LinkOverride.destination(destination))
      Nil
    }
  }

  def steps(options: OptionSource): Iterable[LabeledStep] = {

    def steps(offset: Int) = List(
      1 --> readWrongAddress(offset),
      (2 to 3) --> expectLinkTimeout(Some(s662.Section.severalSecondsMs))
    )
    val step4 = 4 --> describe("Run the checks above again with another destination address")

    s662.Section.prologue ++ steps(offset = 1) ++ List(step4) ++ steps(offset = 2)
  }

}
