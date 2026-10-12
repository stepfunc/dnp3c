// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s817

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.OutstationOptions
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions._
import io.stepfunc.dnp3c.conformance.s8.helpers.CounterEventSteps

object Procedure81725 extends TestProcedure {

  override def id: String = "8.17.2.5"

  override def description: String = "Binary Counters, Event Without Confirm"

  override def requiredOptions: List[OptionCondition] = Inputs.counter.withEvents

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    CounterEventSteps.NoConfirmSteps(options.get(OutstationOptions.appConfirmTimeout))
  }
}
