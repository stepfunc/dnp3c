// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api.options.{ LinkOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.api.{ Label, _ }
import io.stepfunc.dnp3c.conformance.common.LinkSteps._
import io.stepfunc.dnp3c.conformance.v27.SecondaryFrames.twice
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.link.{ Control, LinkFrame, LinkFunction, LinkHeader }
import io.stepfunc.dnp3c.dnp3.{ Comment, LinkOverride, TestDriver }
import io.stepfunc.dnp3c.protocol.Hex
import io.stepfunc.dnp3c.protocol.parsing.UInt8
import io.stepfunc.dnp3c.dnp3.DeferredCheck

object Procedure6_6_3_2 extends ProcedureDefinition {

  override protected def requiredOptions = List(SupportOptions.dataLinkConfirmRequests, SupportOptions.dataLinkRetries)

  // an ACK (control 0x80) with the undefined function code `function`, as in the document's example control 0x83
  private def invalidAck(function: Byte): Control = Control(true, false, false, LinkFunction.unknown(false, function))

  def sendConfirmInvalidFunction(function: Byte): TestStep = new TestStep {

    def description: List[Comment] = {
      val control = invalidAck(function).toByte
      List(
        s"Send an ACK frame (control ${Hex.prefixed(control)}) whose DIR, PRM and DFC bits are valid but whose " +
          s"function code is the undefined value ${Hex.prefixed((control & 0x0F).toByte)}, with the header CRC " +
          "recomputed to match."
      )
    }

    def run(driver: TestDriver): List[DeferredCheck] = {
      driver.writeFrame(LinkFrame.headerOnly(invalidAck(function), driver.remote, driver.local))
      Nil
    }
  }

  override protected def plan(options: OptionSource): Plan = {

    val timeout = options.get(LinkOptions.timeout)
    val responseTimeout = Some(responseTimeoutMs(options))

    def steps(function: Byte): Iterable[LabeledStep] = {
      val response = new UnconfirmedFrame
      List(
        1 --> readClass0(Sequence(0x01.toByte), LinkOverride.control(0xC4.toByte)),
        2 --> response.expect(withFunction(LinkFunction.CONFIRMED_USER_DATA), responseTimeout),
        3 --> sendConfirmInvalidFunction(function),
        4 --> (response.expectRetry(timeout) + disconnect)
      )
    }

    twice(
      options,
      steps(0x03.toByte),
      5 --> describe("Run the checks above again with another undefined function code"),
      steps(0x0A.toByte))
  }
}
