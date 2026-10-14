// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.Validations._
import io.stepfunc.dnp3c.dnp3.Requests
import io.stepfunc.dnp3c.dnp3.app.{ IIN, Sequence, Variation }

object Procedure8_6_3_2 extends ProcedureDefinition {

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    List(
      1 --> send(Requests.readAllObjects(Sequence.zero, Variation.mock(0, 0))),
      2 --> expectSingleResponse(Sequence.zero, containing(IIN.OBJECT_UNKNOWN)),
      3 --> send(Requests.readClass0(Sequence.one)),
      4 --> expectAtLeastOneResponse(Sequence.one, without(IIN.errors))
    )
  }

}
