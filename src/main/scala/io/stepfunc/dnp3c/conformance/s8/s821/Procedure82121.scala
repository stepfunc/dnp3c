// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s821

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group3Var0, Group3Var1, Group3Var2 }
import io.stepfunc.dnp3c.dnp3.Requests

object Procedure82121 extends ProcedureDefinition {

  override protected def requiredOptions: List[OptionCondition] = List(Inputs.doubleBit.points)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    List(
      1 --> PluginSteps.generateDoubleBitBinaryInputPattern(),
      2 --> send(Requests.readAllObjects(Sequence.zero, Group3Var0)),
      (3 to 5) --> expectAtLeastOneResponse(
        Sequence.zero,
        verifyStaticResponse(Set(Group3Var1, Group3Var2)) &&
          PluginValidations.verifyAllDoubleBitBinaryInputCurrentValues().acrossFragments),
      6 --> PluginSteps.generateDoubleBitBinaryInputPattern(),
      7 --> send(Requests.readAllObjects(Sequence.one, Group3Var0)),
      (8 to 9) --> expectAtLeastOneResponse(
        Sequence.one,
        verifyStaticResponse(Set(Group3Var1, Group3Var2)) && verifyFlagsComplyWithFlagNote &&
          PluginValidations.verifyAllDoubleBitBinaryInputCurrentValues().acrossFragments
      )
    )
  }
}
