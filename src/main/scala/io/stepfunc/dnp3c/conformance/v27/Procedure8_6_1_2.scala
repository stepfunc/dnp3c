// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.Validations._
import io.stepfunc.dnp3c.dnp3.Requests
import io.stepfunc.dnp3c.dnp3.app.{ IIN, Sequence }

object Procedure8_6_1_2 extends ProcedureDefinition {

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    List(
      1 --> PluginSteps.cyclePower,
      2 --> send(Requests.readClass1(Sequence.zero)),
      3 --> expectAtLeastOneResponse(Sequence.zero, containing(IIN.DEVICE_RESTART) && verifyConfirmRequestedIfRequired),
      4 --> send(Requests.clearRestartIIN(Sequence.one)),
      5 --> expectSingleResponse(Sequence.one, isNull && without(IIN.DEVICE_RESTART))
    )
  }
}
