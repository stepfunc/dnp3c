// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ LinkOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.common.LinkSteps._
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.dnp3.LinkOverride
import io.stepfunc.dnp3c.dnp3.app.Sequence

/**
 * 6.4.2
 *
 * @param emptyStep14 whether the numbering has 2.7's empty step 14, which 3.1r1 drops, moving the later steps back one
 */
class TestRetries(emptyStep14: Boolean) extends ProcedureDefinition {

  override def requiredOptions = List(SupportOptions.dataLinkConfirmRequests, SupportOptions.dataLinkRetries)

  override def steps(options: OptionSource): Iterable[LabeledStep] = {

    val requirement =
      "This section applies only when the DUT can request link confirmations and retry unconfirmed primary transmissions."
    val step1 = describe(requirement)

    val notExercised = "Not exercised: this tool has no way to make the DUT send TEST LINK or REQUEST LINK STATUS"
    val numRetry = options.get(LinkOptions.numRetry)
    val timeout = options.get(LinkOptions.timeout)
    val responseTimeout = responseTimeoutMs(options)
    val s = if (emptyStep14) 0 else -1

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
      15 + s --> readClass0(Sequence.two, LinkOverride.control(0xC4.toByte)),
      16 + s --> (expectLinkReset(responseTimeout) + sendLinkConfirm),
      (17 + s to 18 + s) --> expectFrameAndRetries(withControl(0x73), 1, timeout, responseTimeout),
      19 + s --> sendLinkConfirm,
      // no retry (0x73) in the window expectNoMoreRetries watches, but a class 0 response longer than one frame goes on
      // with its next frame, the FCB toggled (0x53)
      20 + s --> expectLinkTimeoutOr(withControl(0x53), timeout * 3 / 2),
      21 + s --> describe(notExercised)
    )

  }
}

object Procedure6_4_2 extends TestRetries(emptyStep14 = true)
