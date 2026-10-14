// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ AttributeOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.v27.AttributeValidations._
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.Validations._
import io.stepfunc.dnp3c.dnp3.Requests
import io.stepfunc.dnp3c.dnp3.app.{ IIN, Sequence }

object Procedure8_23_2_3 extends ProcedureDefinition {

  override protected def requiredOptions: List[OptionCondition] = List(SupportOptions.deviceAttributes)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    AttributeOptions.getConfig(options).supported.zipWithIndex.map { case (variation, i) =>
      val seq = Sequence((i % 16).toByte)
      Label(s"steps: 1 to 2, g0v$variation") --> send(Requests.readAttribute(seq, variation)) +
        expectSingleResponse(
          seq,
          without(IIN.errors) && verifySingleAttribute(variation) && PluginValidations.verifyDeviceAttributes())
    }
  }
}
