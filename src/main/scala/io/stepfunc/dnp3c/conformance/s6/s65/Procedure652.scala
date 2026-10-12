// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s6.s65

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions
import io.stepfunc.dnp3c.conformance.s6._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.dnp3.LinkOverride
import io.stepfunc.dnp3c.dnp3.app.Sequence

object Procedure652 extends TestProcedure {

  override def id = "6.5.2"

  override def description = "Test Procedure"

  override def steps(options: OptionSource): Iterable[LabeledStep] = {

    val start = List(
      (1 to 2) --> PluginSteps.setLinkLayerConfirm(false) + PluginSteps.cyclePower,
      3 --> readClass0(Sequence.zero, LinkOverride.control(0xC4.toByte)),
      4 --> expectAtLeastOneResponse(Sequence.zero, withControl(0x44)),
      (5 to 6) --> describe("Not exercised: this tool cannot cause the DUT to send REQUEST LINK STATUS or TEST LINK")
    )

    val optional = if (options.get(SupportOptions.dataLinkConfirmRequests)) {
      List(
        7 --> PluginSteps.setLinkLayerConfirm(true),
        8 --> PluginSteps.cyclePower,
        9 --> readClass0(Sequence.zero.next, LinkOverride.control(0xC4.toByte)),
        10 --> expectLinkReset(responseTimeoutMs(options)),
        11 --> sendLinkConfirm,
        12 --> expectFrame(withControl(0x73), Some(responseTimeoutMs(options))),
        13 --> PluginSteps.setLinkLayerConfirm(false)
      )
    } else {
      List(
        (7 to 13) -->
          describe("The DUT can't request data link confirmations, so the rest of this section doesn't apply")
      )
    }

    start ::: optional

  }

}
