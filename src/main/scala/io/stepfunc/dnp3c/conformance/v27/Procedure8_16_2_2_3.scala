// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions._
import io.stepfunc.dnp3c.conformance.common.FrozenCounterSteps

object Procedure8_16_2_2_3 extends ProcedureDefinition {

  override def requiredOptions: List[OptionCondition] = List(Inputs.frozenCounter.points)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    FrozenCounterSteps.binaryCounterFreezeAndClearSteps(ack = true)
  }
}
