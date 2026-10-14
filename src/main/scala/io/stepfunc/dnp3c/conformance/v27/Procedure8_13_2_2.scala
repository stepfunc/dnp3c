// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions._
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.Validations
import io.stepfunc.dnp3c.conformance.common.Validations.verifyStaticResponse
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group1Var0, Group1Var1, Group1Var2 }
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3._

object Procedure8_13_2_2 extends ProcedureDefinition {

  override def requiredOptions = List(Inputs.binary.points)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    List(
      1 --> PluginSteps.generateBinaryInputPattern(),
      2 --> send(Requests.readAllObjects(Sequence.one, Group1Var0)),
      (3 to 5) --> expectAtLeastOneResponse(
        Sequence.one,
        verifyStaticResponse(Set(Group1Var1, Group1Var2)) &&
          PluginValidations.verifyAllBinaryInputCurrentValues().acrossFragments),
      6 --> PluginSteps.generateBinaryInputPattern(),
      7 --> send(Requests.readAllObjects(Sequence.two, Group1Var0)),
      (8 to 9) --> expectAtLeastOneResponse(
        Sequence.two,
        verifyStaticResponse(Set(Group1Var1, Group1Var2)) &&
          PluginValidations.verifyAllBinaryInputCurrentValues().acrossFragments &&
          Validations.verifyFlagsComplyWithFlagNote
      )
    )
  }
}
