// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s811

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ OutstationOptions, SupportOptions, TestOptions, UnsolicitedOptions }
import io.stepfunc.dnp3c.conformance.s8.helpers.UnsolicitedResponseSteps
import io.stepfunc.dnp3c.conformance.s8.helpers.UnsolicitedResponseSteps.RetryLimit

/**
 * 8.11.2.5, Unsolicited and Polled Responses, whose sections 8.11.2.5.1 to 8.11.2.5.9 run in order, each from where
 * the one before leaves the DUT
 *
 * @param retryLimit what 8.11.2.5.9 does about a limited number of retries, as the revision says, given the options
 */
class UnsolicitedAndPolledResponses(retryLimit: OptionSource => RetryLimit) extends ProcedureDefinition {

  override def requiredOptions: List[OptionCondition] = List(SupportOptions.unsolicitedResponse)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    UnsolicitedResponseSteps.unsolAndPolledResponsesSteps(
      retryLimit(options),
      options.get(UnsolicitedOptions.unsolicitedConfirmTimeoutMs),
      options.get(UnsolicitedOptions.hasMinNumEventsBeforeUnsolicited),
      options.get(OutstationOptions.appConfirmTimeout),
      options.get(UnsolicitedOptions.holdOffMs),
      options.get(SupportOptions.Inputs.doubleBit.points),
      options.get(TestOptions.manualActionWindowMs)
    )
  }
}

/** 2.7's: the DUT must be able to limit its retries, and 8.11.2.5.9 configures 5 */
object Procedure81125 extends UnsolicitedAndPolledResponses(_ => RetryLimit.Tested(5))
