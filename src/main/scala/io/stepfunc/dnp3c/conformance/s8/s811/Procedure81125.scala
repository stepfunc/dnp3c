// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s811

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ OutstationOptions, SupportOptions, TestOptions, UnsolicitedOptions }
import io.stepfunc.dnp3c.conformance.s8.helpers.UnsolicitedResponseSteps

object Procedure81125 extends ProcedureDefinition {

  override def requiredOptions: List[OptionCondition] = List(SupportOptions.unsolicitedResponse)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    UnsolicitedResponseSteps.unsolAndPolledResponsesSteps(
      options.get(UnsolicitedOptions.unsolicitedConfirmTimeoutMs),
      options.get(UnsolicitedOptions.hasMinNumEventsBeforeUnsolicited),
      options.get(OutstationOptions.appConfirmTimeout),
      options.get(UnsolicitedOptions.holdOffMs),
      options.get(SupportOptions.Inputs.doubleBit.points),
      options.get(TestOptions.manualActionWindowMs)
    )
  }
}
