// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s817

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.dnp3.Requests
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.app.objects.Group22Var0

object Procedure81721 extends ProcedureDefinition {

  override protected def requirement: Requirement = Inputs.counter.withoutEvents

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    List(
      1 --> send(Requests.readAllObjects(Sequence.one, Group22Var0)),
      2 -->
        reportByExceptionNotVerified(Some("binary counters")),
      3 --> expectNullResponse(Sequence.one)
    )
  }
}
