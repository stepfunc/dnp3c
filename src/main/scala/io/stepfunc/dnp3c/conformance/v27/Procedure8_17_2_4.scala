// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.CounterOptions
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions._
import io.stepfunc.dnp3c.conformance.common.CounterEventSteps

object Procedure8_17_2_4 extends ProcedureDefinition {

  override def requiredOptions: List[OptionCondition] = Inputs.counter.withEvents

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    CounterEventSteps.CountQualifierSteps(options.get(CounterOptions.numEventsGenerated), true)
  }
}
