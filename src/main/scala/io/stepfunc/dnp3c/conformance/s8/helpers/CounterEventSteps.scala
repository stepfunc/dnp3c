// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.helpers

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group22Var0, Group22Var1, Group22Var2 }
import io.stepfunc.dnp3c.dnp3._

object CounterEventSteps {

  /**
   * @param unconfirmed whether the response to the first read of the generated events is left unconfirmed (step 10),
   *   as in 3.1r1, so that the next read reports them again with the new ones; 2.7 confirms it
   */
  def AllQualifierSteps(unconfirmed: Boolean = false): List[LabeledStep] = {
    val n = if (unconfirmed) 1 else 0
    List(
      1 --> send(Requests.readAllObjects(Sequence.one, Group22Var0)),
      (2 to 3) --> expectEventsEmptied(Sequence.one),
      4 --> send(Requests.readAllObjects(Sequence.two, Group22Var0)),
      5 --> expectNullResponse(Sequence.two),
      6 --> PluginSteps.generateCounterInputData(),
      7 --> send(Requests.readAllObjects(Sequence.three, Group22Var0)),
      (8 to 12 + n) --> expectSingleResponse(
        Sequence.three,
        if (unconfirmed) AutoConfirm.No else AutoConfirm.Yes,
        verifyGroup22AndQualifiers && Validations.verifyConfirmRequested && Validations.verifyFlagsComplyWithFlagNote &&
          verifyProperEvents
      ),
      13 + n --> PluginSteps.recordCurrentCounterValues(),
      14 + n --> PluginSteps.generateCounterInputData(),
      15 + n --> send(Requests.readAllObjects(Sequence.four, Group22Var0)),
      16 + n --> describe(
        s"Each counter's value is the value recorded at step ${13 + n} plus the increments made at step ${14 + n}") +
        expectSingleResponse(
          Sequence.four,
          AutoConfirm.Yes,
          verifyGroup22AndQualifiers && Validations.verifyConfirmRequested &&
            Validations.verifyFlagsComplyWithFlagNote &&
            verifyCurrentValueEvents
        )
    )
  }

  /**
   * @param confirmSteps whether the confirmation of each response is a step of its own, as in 3.1r1 (steps 8 and 15);
   *   2.7 numbers neither, though the master confirms both
   */
  def CountQualifierSteps(
      numEvents: Int,
      twoBytesQualifier: Boolean,
      confirmSteps: Boolean = false): List[LabeledStep] = {
    val a = if (confirmSteps) 1 else 0
    val b = 2 * a
    List(
      1 --> send(Requests.readAllObjects(Sequence.one, Group22Var0)),
      (2 to 3) --> expectEventsEmptied(Sequence.one),
      4 --> PluginSteps.generateExactCounterChangeEvents(numEvents),
      5 --> send(Requests.readCount(Sequence.two, Group22Var0, 1, twoBytesQualifier)),
      (6 to 10 + a) --> expectSingleResponse(
        Sequence.two,
        AutoConfirm.Yes,
        Validations.eventResponse(verifyFirstEvent, verifyGroup22AndQualifiers)
      ),
      11 + a --> send(Requests.readCount(Sequence.three, Group22Var0, numEvents - 1, twoBytesQualifier)),
      (12 + a to 13 + b) --> expectSingleResponse(
        Sequence.three,
        AutoConfirm.Yes,
        Validations.eventResponse(verifyRestEvents, verifyGroup22AndQualifiers)
      ),
      14 + b --> send(Requests.readAllObjects(Sequence.four, Group22Var0)),
      15 + b --> expectNullResponse(Sequence.four)
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
