// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s823

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ AttributeOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.s8.helpers.AttributeValidations._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3.Requests
import io.stepfunc.dnp3c.dnp3.app.{ IIN, Sequence }
import io.stepfunc.dnp3c.dnp3.app.objects.Group0

object Procedure82321 extends ProcedureDefinition {

  override protected def requiredOptions: List[OptionCondition] = List(SupportOptions.deviceAttributes)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    val config = AttributeOptions.getConfig(options)

    List(
      1 --> send(Requests.readAttribute(Sequence.zero, 255)),
      (2 to 5) --> expectAtLeastOneResponse(
        Sequence.zero,
        without(IIN.errors) && verifyAttributeList(config, onlyStandardSet = true).acrossFragments),
      6 --> send(Requests.readAllObjects(Sequence.one, Group0(255))),
      (7 to 10) --> expectAtLeastOneResponse(
        Sequence.one,
        without(IIN.errors) && verifyAttributeList(config, onlyStandardSet = false).acrossFragments)
    )
  }
}
