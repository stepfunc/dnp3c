// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s819

import io.stepfunc.dnp3c.conformance.api.options.OutstationOptions
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.app.objects.Group32Var0
import io.stepfunc.dnp3c.dnp3.{ AutoConfirm, Requests }

object Procedure81922 extends ProcedureDefinition {

  override protected def requiredOptions: List[OptionCondition] = Inputs.analog.withEvents

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {

    def readAnalogEvents(seq: Sequence): TestStep = send(Requests.readAllObjects(seq, Group32Var0))

    List(
      1 --> PluginSteps.generateAnalogInputChangeEvents(),
      2 --> readAnalogEvents(Sequence.zero),
      (3 to 6) --> expectSingleResponse(
        Sequence.zero,
        verifyAnalogEvents && PluginValidations.verifyLatestAnalogInputChangeEvents() &&
          verifyFlagsComplyWithFlagNote &&
          verifyConfirmRequested),
      (7 to 8) --> expectNoRetransmission(options.get(OutstationOptions.appConfirmTimeout)),
      9 --> PluginSteps.generateAnalogInputChangeEvents(),
      10 --> readAnalogEvents(Sequence.one),
      (11 to 14) --> expectSingleResponse(
        Sequence.one,
        AutoConfirm.Yes,
        verifyAnalogEvents && PluginValidations.verifyAnalogInputEventsForCurrentValues() &&
          verifyFlagsComplyWithFlagNote &&
          verifyConfirmRequested
      ),
      15 --> readAnalogEvents(Sequence.two),
      16 --> expectSingleResponse(Sequence.two, isNull)
    )
  }

}
