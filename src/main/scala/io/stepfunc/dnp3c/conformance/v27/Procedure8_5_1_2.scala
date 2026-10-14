// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.Validations._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.Group60Var1

object Procedure8_5_1_2 extends ProcedureDefinition {

  override def steps(options: OptionSource): List[LabeledStep] = {
    List(
      1 --> send(AppFunction.read, Sequence.zero, AllObjectsHeader(Group60Var1)),
      (2 to 3) --> expectAtLeastOneResponse(
        Sequence.zero,
        PluginValidations.verifyAllStaticValues(isClass0 = true).acrossFragments &&
          verifyOnlyTable83Contents(Tables(options)) &&
          verifyFlagsComplyWithFlagNote
      )
    )
  }

}
