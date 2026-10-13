// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s818

import io.stepfunc.dnp3c.conformance.api.options.SupportOptions._
import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations
import io.stepfunc.dnp3c.dnp3.app.objects._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3._

object Procedure81822 extends ProcedureDefinition {

  override protected def requiredOptions: List[OptionCondition] = List(Inputs.analog.points)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    List(
      1 --> PluginSteps.generateAnalogInputPattern(),
      2 --> send(Requests.readAllObjects(Sequence.zero, Group30Var0)),
      (3 to 4) --> expectAtLeastOneResponse(
        Sequence.zero,
        Validations.verifyStaticResponse(Set(Group30Var1, Group30Var2, Group30Var3, Group30Var4)) &&
          Validations.verifyFlagsComplyWithNote5 &&
          PluginValidations.verifyAllAnalogInputsCurrentValues().acrossFragments
      )
    )
  }
}
