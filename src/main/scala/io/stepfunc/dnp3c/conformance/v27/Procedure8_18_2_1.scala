// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions._
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.dnp3.Requests
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.app.objects.Group30Var0

object Procedure8_18_2_1 extends ProcedureDefinition {

  override protected def requiredOptions: List[OptionCondition] = List(!Inputs.analog.points)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    List(
      1 --> send(Requests.readAllObjects(Sequence.zero, Group30Var0)),
      2 --> expectNullOrObjectUnknownResponse(Sequence.zero),
      3 --> describe("optionally, the device may set IIN 2-1")
    )
  }
}
