// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.helpers

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects._
import io.stepfunc.dnp3c.dnp3._

class InputEventSteps(inputType: InputType) {
  import inputType._

  def AllQualifierSteps: List[LabeledStep] = {
    List(
      1 --> send(Requests.readAllObjects(Sequence.one, anyVariation)),
      2 --> expectEventsEmptied(Sequence.one),
      3 --> send(Requests.readAllObjects(Sequence.two, anyVariation)),
      4 --> expectNullResponse(Sequence.two),
      5 --> generateEvents,
      6 --> send(Requests.readAllObjects(Sequence.three, anyVariation)),
      (7 to 11) --> expectSingleResponse(
        Sequence.three,
        AutoConfirm.Yes,
        Validations.eventResponse(verifyLatestEvents, verifyValidGroupVariationAndQualifiers)
      ),
      12 --> send(Requests.readAllObjects(Sequence.four, anyVariation)),
      13 --> expectNullResponse(Sequence.four)
    )
  }

  def CountQualifierSteps(numEvents: Int, twoBytesQualifier: Boolean): List[LabeledStep] = {
    List(
      1 --> send(Requests.readAllObjects(Sequence.one, anyVariation)),
      (2 to 3) --> expectEventsEmptied(Sequence.one),
      4 --> generateExactEvents(numEvents),
      5 --> send(Requests.readCount(Sequence.two, anyVariation, 1, twoBytesQualifier)),
      (6 to 10) --> expectSingleResponse(
        Sequence.two,
        AutoConfirm.Yes,
        Validations.eventResponse(verifyFirstEvent, verifyValidGroupVariationAndQualifiers)
      ),
      11 --> send(Requests.readCount(Sequence.three, anyVariation, numEvents - 1, twoBytesQualifier)),
      (12 to 13) --> expectSingleResponse(
        Sequence.three,
        AutoConfirm.Yes,
        Validations.eventResponse(verifyRestEvents, verifyValidGroupVariationAndQualifiers)
      )
    )
  }

  def NoConfirmSteps(appConfirmTimeoutMs: Int): List[LabeledStep] = {
    List(
      1 --> send(Requests.readAllObjects(Sequence.one, anyVariation)),
      (2 to 3) --> expectEventsEmptied(Sequence.one),
      4 --> generateEvents,
      5 --> send(Requests.readAllObjects(Sequence.two, anyVariation)),
      (6 to 10) --> expectSingleResponse(
        Sequence.two,
        AutoConfirm.No,
        Validations.eventResponse(verifyLatestEvents, verifyValidGroupVariationAndQualifiers)
      ),
      11 --> describe("Withhold the application-layer confirmation for this response."),
      (12 to 13) --> expectNoRetransmission(appConfirmTimeoutMs),
      14 --> generateEvents,
      15 --> send(Requests.readAllObjects(Sequence.three, anyVariation)),
      (16 to 18) --> expectSingleResponse(
        Sequence.three,
        AutoConfirm.Yes,
        Validations.eventResponse(verifyOldAndNewEvents, verifyValidGroupVariationAndQualifiers)
      ),
      19 --> send(Requests.readAllObjects(Sequence.four, anyVariation)),
      20 --> expectNullResponse(Sequence.four)
    )
  }

  def AllQualifierWithoutTimeSteps: List[LabeledStep] = {
    List(
      1 --> send(Requests.readAllObjects(Sequence.one, anyVariation)),
      (2 to 3) --> expectEventsEmptied(Sequence.one),
      4 --> generateEvents,
      5 --> send(Requests.readAllObjects(Sequence.two, noTimeVariation)),
      (6 to 9) --> expectSingleResponse(
        Sequence.two,
        AutoConfirm.Yes,
        Validations.eventResponse(verifyLatestEvents, verifySingleVariationAndQualifiers(noTimeVariation))
      ),
      10 --> send(Requests.readAllObjects(Sequence.three, anyVariation)),
      11 --> expectNullResponse(Sequence.three)
    )
  }

  def CountQualifierWithoutTimeSteps(numEvents: Int, twoBytesQualifier: Boolean): List[LabeledStep] = {
    List(
      1 --> send(Requests.readAllObjects(Sequence.one, anyVariation)),
      (2 to 3) --> expectEventsEmptied(Sequence.one),
      4 --> generateExactEvents(numEvents),
      5 --> send(Requests.readCount(Sequence.two, noTimeVariation, 1, twoBytesQualifier)),
      (6 to 10) --> expectSingleResponse(
        Sequence.two,
        AutoConfirm.Yes,
        Validations.eventResponse(verifyFirstEvent, verifySingleVariationAndQualifiers(noTimeVariation))
      ),
      11 --> send(Requests.readCount(Sequence.three, noTimeVariation, numEvents - 1, twoBytesQualifier)),
      (12 to 14) --> expectSingleResponse(
        Sequence.three,
        AutoConfirm.Yes,
        Validations.eventResponse(verifyRestEvents, verifySingleVariationAndQualifiers(noTimeVariation))
      ),
      15 --> send(Requests.readAllObjects(Sequence.four, anyVariation)),
      16 --> expectNullResponse(Sequence.four)
    )
  }

  def AllQualifierWithTimeSteps(supported: Boolean): List[LabeledStep] = {
    val steps1To5 = List(
      1 --> send(Requests.readAllObjects(Sequence.one, anyVariation)),
      (2 to 3) --> expectEventsEmptied(Sequence.one),
      4 --> generateEvents,
      5 --> send(Requests.readAllObjects(Sequence.two, absoluteTimeVariation))
    )

    val steps6To14 = if (supported) {
      List(
        6 -->
          describe(s"The DUT supports the requested absolute-time event variation ($absoluteTimeVariation)."),
        (7 to 12) --> expectSingleResponse(
          Sequence.two,
          AutoConfirm.Yes,
          Validations.eventResponse(verifyLatestEvents, verifySingleVariationAndQualifiers(absoluteTimeVariation))
        ),
        13 --> send(Requests.readAllObjects(Sequence.three, anyVariation)),
        14 --> expectNullResponse(Sequence.three)
      )
    } else {
      List(
        6 -->
          describe(
            s"The DUT doesn't support the requested absolute-time event variation ($absoluteTimeVariation).") +
          expectSingleResponse(Sequence.two, unsupportedVariationResponse),
        (7 to 14) --> describe("Skipping rest of procedure.")
      )
    }

    steps1To5 ++ steps6To14
  }

  def CountQualifierWithTimeSteps(numEvents: Int, twoBytesQualifier: Boolean): List[LabeledStep] = {
    List(
      1 --> send(Requests.readAllObjects(Sequence.one, anyVariation)),
      (2 to 3) --> expectEventsEmptied(Sequence.one),
      4 --> generateExactEvents(numEvents),
      5 --> send(Requests.readCount(Sequence.two, absoluteTimeVariation, 1, twoBytesQualifier)),
      (6 to 11) --> expectSingleResponse(
        Sequence.two,
        AutoConfirm.Yes,
        Validations.eventResponse(verifyFirstEvent, verifySingleVariationAndQualifiers(absoluteTimeVariation))
      ),
      12 --> send(Requests.readCount(Sequence.three, absoluteTimeVariation, numEvents - 1, twoBytesQualifier)),
      (13 to 15) --> expectSingleResponse(
        Sequence.three,
        AutoConfirm.Yes,
        Validations.eventResponse(verifyRestEvents, verifySingleVariationAndQualifiers(absoluteTimeVariation))
      ),
      16 --> send(Requests.readAllObjects(Sequence.four, anyVariation)),
      17 --> expectNullResponse(Sequence.four)
    )
  }

  def AllQualifierWithRelativeTimeSteps(supported: Boolean): List[LabeledStep] = {
    val steps1To5 = List(
      1 --> send(Requests.readAllObjects(Sequence.one, anyVariation)),
      (2 to 3) --> expectEventsEmptied(Sequence.one),
      4 --> generateEvents,
      5 --> send(Requests.readAllObjects(Sequence.two, relativeTimeVariation))
    )

    val steps6To14 = if (supported) {
      List(
        6 -->
          describe(s"The DUT supports the requested relative-time event variation ($relativeTimeVariation)."),
        (7 to 12) --> expectSingleResponse(
          Sequence.two,
          AutoConfirm.Yes,
          Validations.eventResponse(verifyLatestEvents, verifyVar3WithCtoResponse)
        ),
        13 --> send(Requests.readAllObjects(Sequence.three, anyVariation)),
        14 --> expectNullResponse(Sequence.three)
      )
    } else {
      List(
        6 -->
          describe(
            s"The DUT doesn't support the requested relative-time event variation ($relativeTimeVariation).") +
          expectSingleResponse(Sequence.two, unsupportedVariationResponse),
        (7 to 14) --> describe("Skipping rest of procedure.")
      )
    }

    steps1To5 ++ steps6To14
  }

  def CountQualifierWithRelativeTimeSteps(numEvents: Int, twoBytesQualifier: Boolean): List[LabeledStep] = {
    List(
      1 --> send(Requests.readAllObjects(Sequence.one, anyVariation)),
      (2 to 3) --> expectEventsEmptied(Sequence.one),
      4 --> generateExactEvents(numEvents),
      5 --> send(Requests.readCount(Sequence.two, relativeTimeVariation, 1, twoBytesQualifier)),
      (6 to 11) --> expectSingleResponse(
        Sequence.two,
        AutoConfirm.Yes,
        Validations.eventResponse(verifyFirstEvent, verifyVar3WithCtoResponse)
      ),
      12 --> send(Requests.readCount(Sequence.three, relativeTimeVariation, numEvents - 1, twoBytesQualifier)),
      (13 to 14) --> expectSingleResponse(
        Sequence.three,
        AutoConfirm.Yes,
        Validations.eventResponse(verifyRestEvents, verifyVar3WithCtoResponse)
      ),
      15 --> send(Requests.readAllObjects(Sequence.four, anyVariation)),
      16 --> expectNullResponse(Sequence.four)
    )
  }

  def AllQualifierWithRelativeTimeLongIntervalSteps: List[LabeledStep] = {
    List(
      1 --> send(Requests.readAllObjects(Sequence.one, anyVariation)),
      (2 to 3) --> expectEventsEmptied(Sequence.one),
      4 --> generateEvents,
      // "longer than 0xFFFF ms", with a margin for when the DUT detects and timestamps each batch
      5 --> delay(0xFFFF + 5000),
      6 --> generateEvents,
      7 --> send(Requests.readAllObjects(Sequence.two, relativeTimeVariation)),
      (8 to 14) --> expectSingleResponse(
        Sequence.two,
        AutoConfirm.Yes,
        Validations.eventResponse(verifyOldAndNewEvents, verifyVar3WithCtoResponse && verifyTwoCtosMinimum)
      ),
      15 --> send(Requests.readAllObjects(Sequence.three, relativeTimeVariation)),
      16 --> expectNullResponse(Sequence.three)
    )
  }

  // events of any of the input's variations, a relative-time event preceded by a CTO (Table 8-4)
  private[helpers] def verifyValidGroupVariationAndQualifiers: MessageValidation =
    Validations.eventsWithCtos(List(noTimeVariation, absoluteTimeVariation, relativeTimeVariation))

  // relative-time events only, each preceded by a CTO
  private[helpers] def verifyVar3WithCtoResponse: MessageValidation =
    Validations.eventsWithCtos(List(relativeTimeVariation))

  private def verifySingleVariationAndQualifiers(variation: Variation): MessageValidation =
    Validations.verifyEvents(Set(variation))

  private def verifyTwoCtosMinimum: MessageValidation = {
    val ctoVariations = List(Group51Var1, Group51Var2)

    MessageValidation.check("Verify that the response contains at least two CTO headers (g51v1/g51v2), for the event batches generated more than 0xFFFF ms apart") {
      message =>
        if (message.headers.count(header => ctoVariations.contains(header.variation)) < 2) {
          throw new Exception("Response does not contain enough g51v1/g51v2")
        }
    }
  }
}
