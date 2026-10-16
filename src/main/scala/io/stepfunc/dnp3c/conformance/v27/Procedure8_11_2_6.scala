// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ SupportOptions, TestOptions, UnsolicitedOptions }
import io.stepfunc.dnp3c.conformance.common.UnsolicitedResponseSteps

object Procedure8_11_2_6 extends ProcedureDefinition {

  override def requiredOptions: List[OptionCondition] = List(SupportOptions.unsolicitedResponse)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    UnsolicitedResponseSteps.offSteps(
      options.get(UnsolicitedOptions.unsolicitedConfirmTimeoutMs),
      options.get(UnsolicitedOptions.holdOffMs),
      options.get(TestOptions.timingToleranceMs))
  }
}
