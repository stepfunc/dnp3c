// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s86.s862

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3.Requests
import io.stepfunc.dnp3c.dnp3.app.objects.Group60Var1
import io.stepfunc.dnp3c.dnp3.app.{ AppFunction, IIN, Sequence }

object Procedure8622 extends TestProcedure {

  override def id: String = "8.6.2.2"

  override def description: String = "Test Procedure"

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    List(
      1 --> send(Requests.allObjects(AppFunction.unknown(0x70.toByte), Sequence.zero, Group60Var1)),
      2 --> expectSingleResponse(Sequence.zero, containing(IIN.FUNC_NOT_SUPPORTED)),
      3 --> send(Requests.readClass0(Sequence.one)),
      4 --> expectAtLeastOneResponse(Sequence.one, without(IIN.errors))
    )
  }
}
