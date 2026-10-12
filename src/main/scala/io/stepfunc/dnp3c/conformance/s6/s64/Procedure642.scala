// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s6.s64

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ LinkOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.s6._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.dnp3.LinkOverride
import io.stepfunc.dnp3c.dnp3.app.Sequence

object Procedure642 extends TestProcedure {

  override def id = "6.4.2"

  override def description = "Test Procedure"

  override def requiredOptions = List(SupportOptions.dataLinkConfirmRequests, SupportOptions.dataLinkRetries)

  override def steps(options: OptionSource): Iterable[LabeledStep] = {

    val requirement =
      "This section applies only when the DUT can request link confirmations and retry unconfirmed primary transmissions."
    val step1 = describe(requirement)

    val notExercised = "Not exercised: this tool has no way to make the DUT send TEST LINK or REQUEST LINK STATUS"
    val numRetry = options.get(LinkOptions.numRetry)
    val timeout = options.get(LinkOptions.timeout)
    val responseTimeout = responseTimeoutMs(options)

    List(
      1 --> step1,
      2 --> PluginSteps.setLinkLayerConfirm(true),
      3 --> PluginSteps.cyclePower,
      4 --> readClass0(Sequence.zero, LinkOverride.control(0xC4.toByte)),
      (5 to 7) --> expectFrameAndRetries(isLinkReset, numRetry, timeout, responseTimeout) +
        expectNoMoreRetries(timeout),
      8 --> readClass0(Sequence.one, LinkOverride.control(0xC4.toByte)),
      9 --> (expectLinkReset(responseTimeout) + sendLinkConfirm),
      (10 to 12) --> expectFrameAndRetries(isConfirmedUserData, numRetry, timeout, responseTimeout) +
        expectNoMoreRetries(timeout),
      13 --> describe(notExercised),
      15 --> readClass0(Sequence.two, LinkOverride.control(0xC4.toByte)),
      16 --> (expectLinkReset(responseTimeout) + sendLinkConfirm),
      (17 to 18) --> expectFrameAndRetries(withControl(0x73), 1, timeout, responseTimeout),
      19 --> sendLinkConfirm,
      // no retry (0x73) in the window expectNoMoreRetries watches, but a class 0 response longer than one frame goes on
      // with its next frame, the FCB toggled (0x53)
      20 --> expectLinkTimeoutOr(withControl(0x53), timeout * 3 / 2),
      21 --> describe(notExercised)
    )

  }
}
