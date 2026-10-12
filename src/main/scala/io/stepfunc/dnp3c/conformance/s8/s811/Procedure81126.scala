// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s811

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ SupportOptions, UnsolicitedOptions }
import io.stepfunc.dnp3c.conformance.s8.helpers.UnsolicitedResponseSteps

object Procedure81126 extends TestProcedure {

  override def id: String = "8.11.2.6"

  override def description: String = "Unsolicited Responses Off"

  override def requiredOptions: List[OptionCondition] = List(SupportOptions.unsolicitedResponse)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    UnsolicitedResponseSteps.offSteps(
      options.get(UnsolicitedOptions.unsolicitedConfirmTimeoutMs),
      options.get(UnsolicitedOptions.holdOffMs))
  }
}
