// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.helpers

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group22Var0, Group22Var1, Group22Var2 }
import io.stepfunc.dnp3c.dnp3._

object CounterEventSteps {
  def AllQualifierSteps: List[LabeledStep] = {
    List(
      1 --> send(Requests.readAllObjects(Sequence.one, Group22Var0)),
      (2 to 3) --> expectEventsEmptied(Sequence.one),
      4 --> send(Requests.readAllObjects(Sequence.two, Group22Var0)),
      5 --> expectNullResponse(Sequence.two),
      6 --> PluginSteps.generateCounterInputData(),
      7 --> send(Requests.readAllObjects(Sequence.three, Group22Var0)),
      (8 to 12) --> expectSingleResponse(
        Sequence.three,
        AutoConfirm.Yes,
        verifyGroup22AndQualifiers && Validations.verifyConfirmRequested && Validations.verifyFlagsComplyWithFlagNote &&
          verifyProperEvents
      ),
      13 --> PluginSteps.recordCurrentCounterValues(),
      14 --> PluginSteps.generateCounterInputData(),
      15 --> send(Requests.readAllObjects(Sequence.four, Group22Var0)),
      16 --> describe("Each counter's value is the value recorded at step 13 plus the increments made at step 14") +
        expectSingleResponse(
          Sequence.four,
          AutoConfirm.Yes,
          verifyGroup22AndQualifiers && Validations.verifyConfirmRequested &&
            Validations.verifyFlagsComplyWithFlagNote &&
            verifyCurrentValueEvents
        )
    )
  }

  def CountQualifierSteps(numEvents: Int, twoBytesQualifier: Boolean): List[LabeledStep] = {
    List(
      1 --> send(Requests.readAllObjects(Sequence.one, Group22Var0)),
      (2 to 3) --> expectEventsEmptied(Sequence.one),
      4 --> PluginSteps.generateExactCounterChangeEvents(numEvents),
      5 --> send(Requests.readCount(Sequence.two, Group22Var0, 1, twoBytesQualifier)),
      (6 to 10) --> expectSingleResponse(
        Sequence.two,
        AutoConfirm.Yes,
        Validations.eventResponse(verifyFirstEvent, verifyGroup22AndQualifiers)
      ),
      11 --> send(Requests.readCount(Sequence.three, Group22Var0, numEvents - 1, twoBytesQualifier)),
      (12 to 13) --> expectSingleResponse(
        Sequence.three,
        AutoConfirm.Yes,
        Validations.eventResponse(verifyRestEvents, verifyGroup22AndQualifiers)
      ),
      14 --> send(Requests.readAllObjects(Sequence.four, Group22Var0)),
      15 --> expectNullResponse(Sequence.four)
    )
  }

  def NoConfirmSteps(appConfirmTimeoutMs: Int): List[LabeledStep] = {
    List(
      1 --> send(Requests.readAllObjects(Sequence.one, Group22Var0)),
      (2 to 3) --> expectEventsEmptied(Sequence.one),
      4 --> PluginSteps.generateCounterInputData(),
      5 --> send(Requests.readAllObjects(Sequence.two, Group22Var0)),
      (6 to 7) -->
        expectSingleResponse(Sequence.two, AutoConfirm.No, verifyProperEvents && Validations.verifyConfirmRequested),
      8 --> describe("Withhold the application-layer confirmation for this response."),
      (9 to 10) --> expectNoRetransmission(appConfirmTimeoutMs),
      11 --> PluginSteps.generateCounterInputData(),
      12 --> send(Requests.readAllObjects(Sequence.three, Group22Var0)),
      (13 to 18) --> expectSingleResponse(
        Sequence.three,
        AutoConfirm.Yes,
        Validations.eventResponse(verifyCurrentValueEvents, verifyGroup22AndQualifiers)
      ),
      19 --> send(Requests.readAllObjects(Sequence.four, Group22Var0)),
      20 --> expectNullResponse(Sequence.four)
    )
  }

  private val verifyProperEvents: MessageValidation = PluginValidations.verifyLatestCounterChangeEvents()

  private val verifyFirstEvent: MessageValidation = PluginValidations.verifyFirstCounterChangeEvent()

  private val verifyRestEvents: MessageValidation = PluginValidations.verifyRestCounterChangeEvents()

  private val verifyCurrentValueEvents: MessageValidation = PluginValidations.verifyCounterEventsForCurrentValues()

  private def verifyGroup22AndQualifiers: MessageValidation =
    Validations.verifyEvents(Set(Group22Var1, Group22Var2))
}
