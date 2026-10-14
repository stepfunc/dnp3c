// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions._
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.dnp3.Requests
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.app.objects.Group1Var0

object Procedure8_13_2_1 extends ProcedureDefinition {

  override def requiredOptions: List[OptionCondition] = List(!Inputs.binary.points)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    List(
      1 --> send(Requests.readAllObjects(Sequence.one, Group1Var0)),
      2 --> expectNullOrObjectUnknownResponse(Sequence.one)
    )
  }
}
