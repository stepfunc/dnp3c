// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s811

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ LinkOptions, SupportOptions, UnsolicitedOptions }
import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.UnconfirmedResponse
import io.stepfunc.dnp3c.conformance.s8.helpers.UnsolicitedResponseSteps.expectNoEventsUnsolicited
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.{ AutoConfirm, LinkOverride, Requests }
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE

object Procedure81121 extends ProcedureDefinition {

  override def requiredOptions: List[OptionCondition] = List(SupportOptions.unsolicitedResponse)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    val unsolConfirmTimeoutMs = options.get(UnsolicitedOptions.unsolicitedConfirmTimeoutMs)
    val holdOffMs = options.get(UnsolicitedOptions.holdOffMs)
    val restartResponse = new UnconfirmedResponse(unsolConfirmTimeoutMs)
    val nullWithRestart = Validations.containing(IIN.DEVICE_RESTART) && Validations.isNull
    val originalAddress = options.get(LinkOptions.source)
    val otherMasterAddress = options.get(UnsolicitedOptions.otherMasterAddress)

    List(
      1 --> describe("Verify that offline configuration can enable or disable unsolicited responses") +
        PluginSteps.enableUnsolicitedResponse(true),
      2 --> describe(
        "Not verified by this tool: that offline configuration can set the unsolicited confirmation timeout from 1 " +
          "second or less to 1 minute or more"),
      3 --> PluginSteps.setUnsolicitedResponseTimeout(unsolConfirmTimeoutMs),
      4 -->
        describe("Verify that offline configuration can set the destination master address for unsolicited responses") +
        PluginSteps.setMasterAddress(otherMasterAddress),
      5 --> PluginSteps.cyclePower,
      6 --> expectUnsolicitedResponse(
        AutoConfirm.No,
        FrameValidator.none,
        Validations.isNull,
        LinkOverride(None, Some(UInt16LE(otherMasterAddress)))),
      7 --> PluginSteps.setMasterAddress(originalAddress),
      8 --> PluginSteps.cyclePower,
      (9 to 14) --> expectUnsolicitedResponse(restartResponse.hold, nullWithRestart),
      (15 to 18) --> restartResponse.expectRetry(nullWithRestart) + restartResponse.expectRetry(nullWithRestart),
      19 --> send(Requests.clearRestartIIN(Sequence.zero)),
      20 --> expectSingleResponse(Sequence.zero, Validations.isNull && Validations.without(IIN.DEVICE_RESTART)),
      (21 to 22) --> restartResponse.expectRetry(Validations.isNull && Validations.without(IIN.DEVICE_RESTART)),
      23 --> send(Requests.readClass0(Sequence.one)),
      24 -->
        // the procedure expects this READ answered while the startup unsolicited response is still unconfirmed
        // (8.11.1.3); IEEE 1815's outstation state table would defer it until the confirm. An open question: the
        // procedure is followed here.
        expectAtLeastOneResponse(Sequence.one),
      (25 to 26) --> restartResponse.expectRetry(Validations.isNull, AutoConfirm.Wrong),
      (27 to 28) --> restartResponse.expectRetry(Validations.isNull, AutoConfirm.Yes),
      29 --> PluginSteps.generateUnsolicitedEvents(),
      30 --> expectNoEventsUnsolicited(holdOffMs, unsolConfirmTimeoutMs)
    )
  }
}
