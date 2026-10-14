// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s6.s66.s663

import io.stepfunc.dnp3c.conformance.api.options.{ LinkOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.api.{ Label, _ }
import io.stepfunc.dnp3c.conformance.s6._
import io.stepfunc.dnp3c.conformance.s6.s66.s663.Section.twice
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.link.{ Control, LinkFrame, LinkFunction, LinkHeader }
import io.stepfunc.dnp3c.dnp3.{ Comment, LinkOverride, TestDriver }
import io.stepfunc.dnp3c.protocol.parsing.UInt8
import io.stepfunc.dnp3c.dnp3.DeferredCheck

object Procedure6633 extends ProcedureDefinition {

  override protected def requiredOptions = List(SupportOptions.dataLinkConfirmRequests, SupportOptions.dataLinkRetries)

  def sendConfirmWithInvalidDestination(offset: Short): TestStep = new TestStep {

    def description: List[Comment] = {
      List("Send an ACK frame addressed to some address other than the DUT's")
    }

    def run(driver: TestDriver): List[DeferredCheck] = {
      val destination = driver.remote.copy(value = driver.remote.value + offset)
      driver.writeFrame(LinkFrame.headerOnly(Control.ack, destination, driver.local))
      Nil
    }
  }

  override protected def plan(options: OptionSource): Plan = {

    val timeout = options.get(LinkOptions.timeout)
    val responseTimeout = Some(responseTimeoutMs(options))

    def steps(offset: Short): Iterable[LabeledStep] = {
      val response = new UnconfirmedFrame
      List(
        2 --> readClass0(Sequence(0x01.toByte), LinkOverride.control(0xC4.toByte)),
        3 --> response.expect(withFunction(LinkFunction.CONFIRMED_USER_DATA), responseTimeout),
        4 --> sendConfirmWithInvalidDestination(offset),
        5 --> (response.expectRetry(timeout) + disconnect)
      )
    }

    twice(
      options,
      steps(offset = 1),
      6 --> describe("Run the checks above again with another wrong destination"),
      steps(offset = 2))
  }
}
