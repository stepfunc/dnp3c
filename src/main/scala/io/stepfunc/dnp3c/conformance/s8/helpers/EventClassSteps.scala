// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.helpers

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3._

object EventClassSteps {

  def AllQualifierSteps(
      data: EventClassData,
      supportClass: Boolean,
      allowDoubleBit: Boolean): List[LabeledStep] = {
    val firstSteps = List(
      1 --> send(data.read(Sequence.zero)),
      2 --> reportByExceptionNotVerified(None)
    )

    val restOfSteps = if (supportClass) {
      List(
        (3 to 5) -->
          expectAtLeastOneResponse(Sequence.zero, verifyOnlyTable84Contents(allowDoubleBit) && without(IIN.errors)),
        6 --> PluginSteps.generateClassEvents(data.eventClass),
        7 --> send(data.read(Sequence.one)),
        (8 to 12) --> expectSingleResponse(
          Sequence.one,
          AutoConfirm.Yes,
          eventResponse(
            PluginValidations.verifyLatestClassEvents(data.eventClass),
            verifyOnlyTable84Contents(allowDoubleBit))
        ), // confirmed, though the document doesn't say to: the later steps expect these events gone
        13 --> send(data.read(Sequence.two)),
        14 --> expectNullResponse(Sequence.two)
      )
    } else {
      List(
        (3 to 14) --> describe(s"The DUT doesn't support ${data.eventClass} events") +
          expectNullResponse(Sequence.zero)
      )
    }

    firstSteps ++ restOfSteps

  }

  def CountQualifierSteps(
      data: EventClassData,
      numEvents: Int,
      twoBytesQualifier: Boolean,
      allowDoubleBit: Boolean): List[LabeledStep] = {
    List(
      1 --> send(data.read(Sequence.zero)),
      (2 to 3) --> expectEventsEmptied(Sequence.zero),
      4 --> PluginSteps.generateExactClassEvents(data.eventClass, numEvents),
      5 --> send(Requests.readCount(Sequence.one, data.variation, 1, twoBytesQualifier)),
      (6 to 10) --> expectSingleResponse(
        Sequence.one,
        AutoConfirm.Yes,
        eventResponse(
          PluginValidations.verifyFirstClassEvent(data.eventClass),
          verifyOnlyTable84Contents(allowDoubleBit))
      ), // confirmed, though the document doesn't say to: the later steps expect these events gone
      11 --> send(Requests.readCount(Sequence.two, data.variation, numEvents - 1, twoBytesQualifier)),
      (12 to 13) --> expectSingleResponse(
        Sequence.two,
        AutoConfirm.Yes,
        eventResponse(
          PluginValidations.verifyRestClassEvents(data.eventClass),
          verifyOnlyTable84Contents(allowDoubleBit))
      ), // confirmed, though the document doesn't say to: the later steps expect these events gone
      14 --> send(data.read(Sequence.three)),
      15 --> expectNullResponse(Sequence.three)
    )
  }

  def NoConfirmSteps(
      data: EventClassData,
      appConfirmTimeoutMs: Int,
      allowDoubleBit: Boolean): List[LabeledStep] = {
    List(
      1 --> send(data.read(Sequence.zero)),
      (2 to 3) --> expectEventsEmptied(Sequence.zero),
      4 --> PluginSteps.generateClassEvents(data.eventClass),
      5 --> send(data.read(Sequence.one)),
      (6 to 10) --> expectSingleResponse(
        Sequence.one,
        AutoConfirm.No,
        eventResponse(
          PluginValidations.verifyLatestClassEvents(data.eventClass),
          verifyOnlyTable84Contents(allowDoubleBit))
      ),
      11 --> describe("Withhold the application-layer confirmation for this response."),
      (12 to 13) --> expectNoRetransmission(appConfirmTimeoutMs),
      14 --> PluginSteps.generateClassEvents(data.eventClass),
      15 --> send(data.read(Sequence.two)),
      // confirmed in step 18, not on receipt; a manual answer waits until the steps are done, so that a tester's time
      // doesn't make the confirmation later than the DUT's confirm timeout
      (16 to 17) --> expectSingleResponse(
        Sequence.two,
        AutoConfirm.No,
        eventResponse(
          PluginValidations.verifyOldAndNewClassEvents(data.eventClass).deferrable,
          verifyOnlyTable84Contents(allowDoubleBit))
      ),
      18 --> confirm(Sequence.two),
      19 --> send(data.read(Sequence.three)),
      20 --> expectNullResponse(Sequence.three)
    )
  }

}
