// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s816

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.dnp3.Requests
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.app.objects.Group20Var0

object Procedure816121 extends TestProcedure {

  override def id: String = "8.16.1.2.1"

  override def description: String = "Device does not have Binary Counters"

  override def requiredOptions: List[OptionCondition] = List(!Inputs.counter.points)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    List(
      1 --> send(Requests.readAllObjects(Sequence.one, Group20Var0)),
      2 --> expectNullOrObjectUnknownResponse(Sequence.one)
    )
  }
}
