// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s819

import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.app.objects.Group32Var0
import io.stepfunc.dnp3c.dnp3.{ AutoConfirm, Requests }

/**
 * 8.19.2's Analog Input Change
 *
 * @param confirmStep whether the confirmation of the response with the events is a step of its own, as in 3.1r1 (step
 *   10); 2.7 doesn't number it, though the master confirms the response
 */
class AnalogInputChange(confirmStep: Boolean) extends ProcedureDefinition {

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {

    def readAnalogEvents(seq: Sequence): TestStep = send(Requests.readAllObjects(seq, Group32Var0))

    val supportsAnalogEvents = options.get(Inputs.analog.points) && options.get(Inputs.analog.events)

    val firstSteps = List(
      1 --> readAnalogEvents(Sequence.zero),
      2 --> reportByExceptionNotVerified(Some("analog inputs"))
    )

    def notSupportedStep: LabeledStep = 3 --> expectSingleResponse(Sequence.zero, isNull)

    def supportedSteps: List[LabeledStep] = {
      val c = if (confirmStep) 1 else 0
      List(
        // step 3 is for a DUT without analog input events; this one reads and confirms the response to step 1
        3 --> describe("The DUT supports analog input events: confirm the response to step 1") +
          expectEventsEmptied(Sequence.zero),
        4 --> PluginSteps.generateAnalogInputChangeEvents(),
        5 --> readAnalogEvents(Sequence.one),
        (6 to 9 + c) --> expectSingleResponse(
          Sequence.one,
          AutoConfirm.Yes,
          verifyAnalogEvents && PluginValidations.verifyLatestAnalogInputChangeEvents() &&
            verifyFlagsComplyWithFlagNote &&
            verifyConfirmRequested
        ),
        10 + c --> readAnalogEvents(Sequence.two),
        11 + c --> expectSingleResponse(Sequence.two, isNull),
        12 + c --> PluginSteps.generateAnalogInputChangeEvents(),
        13 + c --> readAnalogEvents(Sequence.three),
        14 + c --> expectSingleResponse(
          Sequence.three,
          AutoConfirm.Yes,
          verifyAnalogEvents && PluginValidations.verifyLatestAnalogInputChangeEvents())
      )
    }

    if (supportsAnalogEvents) firstSteps ++ supportedSteps else firstSteps :+ notSupportedStep
  }

}

object Procedure81921 extends AnalogInputChange(confirmStep = false)
