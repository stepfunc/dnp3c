// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s6.s61.s612

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ BehaviorOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.s6._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.dnp3.LinkOverride
import io.stepfunc.dnp3c.dnp3.app.{ MutableSequence, Sequence }

object Procedure612 extends ProcedureDefinition {

  private def ackThenResponse(seq: Sequence): TestStep = {
    expectFrame(withControl(0x00.toByte)) + expectAtLeastOneResponse(seq)
  }

  override def steps(options: OptionSource): Iterable[LabeledStep] = {

    val seq = MutableSequence.zero

    requiredSteps(seq, options) ++ optionalSteps(seq, options)
  }

  private def requiredSteps(seq: MutableSequence, options: OptionSource): Iterable[LabeledStep] = {

    List(
      1 --> PluginSteps.cyclePower,
      2 --> readClass0(seq, LinkOverride.control(0xC4.toByte)),
      3 --> expectAtLeastOneResponse(seq),
      4 --> readClass0(seq.increment(), LinkOverride.control(0xF3.toByte)),
      5 --> expectLinkTimeoutOr(isNack, responseTimeoutMs(options)),
      6 --> readClass0(seq.increment(), LinkOverride.control(0xD3.toByte)),
      7 --> expectLinkTimeoutOr(isNack, responseTimeoutMs(options)),
      8 --> sendLinkControl(0xC0.toByte),
      9 --> expectFrame(withControl(0x00.toByte)),
      10 --> readClass0(seq.increment(), LinkOverride.control(0xF3.toByte)),
      11 --> ackThenResponse(seq),
      (12 to 13) --> repeat(3) {
        readClass0(seq.increment(), LinkOverride.control(0xD3.toByte)) + ackThenResponse(seq) +
          readClass0(seq.increment(), LinkOverride.control(0xF3.toByte)) + ackThenResponse(seq)
      },
      14 --> sendLinkControl(0xC0.toByte),
      15 --> expectFrame(withControl(0x00.toByte)),
      16 --> readClass0(seq.increment(), LinkOverride.control(0xD3.toByte)),
      17 --> (expectFrame(withControl(0x00.toByte)) + expectNoAnswer(options)),
      18 --> readClass0(seq.increment(), LinkOverride.control(0xF3.toByte)),
      19 --> (expectFrame(withControl(0x00.toByte)) + expectAtLeastOneResponse(seq)),
      20 --> readClass0(seq.increment(), LinkOverride.control(0xF3.toByte)),
      21 --> (expectFrame(withControl(0x00.toByte)) + expectNoAnswer(options))
    )
  }

  private def optionalSteps(seq: MutableSequence, options: OptionSource): Iterable[LabeledStep] = {

    val responseTimeout = Some(responseTimeoutMs(options))
    val response = new UnconfirmedFrame

    def start = List(
      23 --> PluginSteps.setLinkLayerConfirm(true),
      24 --> PluginSteps.cyclePower,
      25 --> readClass0(seq.increment(), LinkOverride.control(0xC4.toByte)),
      26 --> expectFrame(withControl(0x40), responseTimeout),
      27 --> sendLinkControl(0x80.toByte),
      28 --> response.expect(withControl(0x73), responseTimeout),
      29 --> sendLinkControl(0x81.toByte)
    )

    def branch = if (options.get(BehaviorOptions.resetsLinkUponReceivingNack)) {
      List(
        30 --> {
          expectFrame(withControl(0x40), responseTimeout) +
            sendLinkControl(0x80.toByte) +
            response.expectRepeat(responseTimeout)
        }
      )
    } else {
      List(
        25 --> readClass0(seq.increment(), LinkOverride.control(0xC4.toByte)),
        26 --> expectFrame(withControl(0x40), responseTimeout),
        27 --> sendLinkControl(0x80.toByte),
        28 --> expectFrame(withControl(0x73), responseTimeout)
      ).map(_.appendLabel(" (step 31: no link reset at step 30, so repeat)"))
    }

    if (options.get(SupportOptions.dataLinkConfirmRequests)) start ++ branch
    else List(22 --> describe("The DUT can't request data link confirms, so the rest of this procedure doesn't apply"))

  }
}
