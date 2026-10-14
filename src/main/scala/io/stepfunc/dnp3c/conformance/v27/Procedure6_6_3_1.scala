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

object Procedure6_6_3_1 extends ProcedureDefinition {

  override protected def requiredOptions: List[OptionCondition] =
    List(SupportOptions.dataLinkConfirmRequests, SupportOptions.dataLinkRetries)

  def sendConfirmInvalidStartOctets(first: Byte, second: Byte): TestStep = new TestStep {

    def description: List[Comment] = {
      val sync1 = Hex.prefixed(first)
      val sync2 = Hex.prefixed(second)
      List(s"Send a ACK frame modified with invalid start octets: {$sync1, $sync2}")
    }

    def run(driver: TestDriver): List[DeferredCheck] = {
      driver.write(LinkFrame.headerOnly(Control.ack, driver.remote, driver.local).toBytesWithStart(first, second))
      Nil
    }
  }

  override protected def plan(options: OptionSource): Plan = {

    val timeout = options.get(LinkOptions.timeout)
    val responseTimeout = Some(responseTimeoutMs(options))

    def steps(first: Byte, second: Byte): Iterable[LabeledStep] = {
      val response = new UnconfirmedFrame
      List(
        1 --> readClass0(Sequence.one, LinkOverride.control(0xC4.toByte)),
        2 --> response.expect(withFunction(LinkFunction.CONFIRMED_USER_DATA), responseTimeout),
        3 --> sendConfirmInvalidStartOctets(first, 0x64.toByte),
        // confirm the retry, so the DUT doesn't retransmit it again while answering the next request
        4 --> response.expectRetry(timeout) + sendLinkConfirm,
        5 --> readClass0(Sequence.two, LinkOverride.control(0xC4.toByte)),
        6 --> response.expect(withFunction(LinkFunction.CONFIRMED_USER_DATA), responseTimeout),
        7 --> sendConfirmInvalidStartOctets(0x05.toByte, second),
        8 --> (response.expectRetry(timeout) + disconnect)
      )
    }

    twice(
      options,
      steps(0x09.toByte, 0xFF.toByte),
      9 --> describe("Run the checks above again with other bad start octets"),
      steps(0xAA.toByte, 0xFE.toByte))
  }
}
