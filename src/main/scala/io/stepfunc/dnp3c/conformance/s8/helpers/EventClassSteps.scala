// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.helpers

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3._

object EventClassSteps {

  /**
   * The class's IIN1 bit (IIN1.1 to IIN1.3), which says that events of the class remain: clear once a response has
   * reported them all
   */
  private def classBit(data: EventClassData): IIN = data.eventClass match {
    case EventClass.Class1 => IIN.CLASS1_EVENTS
    case EventClass.Class2 => IIN.CLASS2_EVENTS
    case EventClass.Class3 => IIN.CLASS3_EVENTS
    case other => throw new Exception(s"Unexpected event class: $other")
  }

  /**
   * @param classBitCleared whether the response with all the events is also checked for the class's IIN1 bit clear,
   *   in a step of its own, as 3.1r1 does (step 9)
   */
  def AllQualifierSteps(
      data: EventClassData,
      supportClass: Boolean,
      tables: Tables,
      classBitCleared: Boolean): List[LabeledStep] = {
    // the steps after the IIN1 check, which it moves on by one, and the explicit confirmation 3.1r1 adds (step 14)
    val shift = if (classBitCleared) 2 else 0
    val firstSteps = List(
      1 --> send(data.read(Sequence.zero)),
      2 --> reportByExceptionNotVerified(None)
    )

    val restOfSteps = if (supportClass) {
      val events = PluginValidations.verifyLatestClassEvents(data.eventClass)
      List(
        (3 to 5) --> expectAtLeastOneResponse(Sequence.zero, verifyOnlyTable84Contents(tables) && without(IIN.errors)),
        6 --> PluginSteps.generateClassEvents(data.eventClass),
        7 --> send(data.read(Sequence.one)),
        (8 to 12 + shift) --> expectSingleResponse(
          Sequence.one,
          AutoConfirm.Yes,
          eventResponse(
            if (classBitCleared) events && without(classBit(data)) else events,
            verifyOnlyTable84Contents(tables))
        ), // confirmed, though 2.7 doesn't say to: the later steps expect these events gone
        13 + shift --> send(data.read(Sequence.two)),
        14 + shift --> expectNullResponse(Sequence.two)
      )
    } else {
      List(
        (3 to 14 + shift) --> describe(s"The DUT doesn't support ${data.eventClass} events") +
          expectNullResponse(Sequence.zero)
      )
    }

    firstSteps ++ restOfSteps

  }

  /**
   * Which confirmations of a count qualifier test's two responses are steps of their own. 2.7 numbers neither, though
   * the master confirms both; 3.1r1 numbers the second, and the first in every test but 8.5.2.2.2.
   */
  final case class CountConfirmSteps(first: Boolean, second: Boolean)

  object CountConfirmSteps {
    val none: CountConfirmSteps = CountConfirmSteps(first = false, second = false)
    val both: CountConfirmSteps = CountConfirmSteps(first = true, second = true)
  }

  def CountQualifierSteps(
      data: EventClassData,
      numEvents: Int,
      twoBytesQualifier: Boolean,
      tables: Tables,
      confirms: CountConfirmSteps): List[LabeledStep] = {
    val a = if (confirms.first) 1 else 0
    val b = a + (if (confirms.second) 1 else 0)
    List(
      1 --> send(data.read(Sequence.zero)),
      (2 to 3) --> expectEventsEmptied(Sequence.zero),
      4 --> PluginSteps.generateExactClassEvents(data.eventClass, numEvents),
      5 --> send(Requests.readCount(Sequence.one, data.variation, 1, twoBytesQualifier)),
      (6 to 10 + a) --> expectSingleResponse(
        Sequence.one,
        AutoConfirm.Yes,
        eventResponse(PluginValidations.verifyFirstClassEvent(data.eventClass), verifyOnlyTable84Contents(tables))
      ), // confirmed, though the document may not say to: the later steps expect these events gone
      11 + a --> send(Requests.readCount(Sequence.two, data.variation, numEvents - 1, twoBytesQualifier)),
      (12 + a to 13 + b) --> expectSingleResponse(
        Sequence.two,
        AutoConfirm.Yes,
        eventResponse(PluginValidations.verifyRestClassEvents(data.eventClass), verifyOnlyTable84Contents(tables))
      ), // confirmed, though the document may not say to: the later steps expect these events gone
      14 + b --> send(data.read(Sequence.three)),
      15 + b --> expectNullResponse(Sequence.three)
    )
  }

  def NoConfirmSteps(data: EventClassData, appConfirmTimeoutMs: Int, tables: Tables): List[LabeledStep] = {
    List(
      1 --> send(data.read(Sequence.zero)),
      (2 to 3) --> expectEventsEmptied(Sequence.zero),
      4 --> PluginSteps.generateClassEvents(data.eventClass),
      5 --> send(data.read(Sequence.one)),
      (6 to 10) --> expectSingleResponse(
        Sequence.one,
        AutoConfirm.No,
        eventResponse(PluginValidations.verifyLatestClassEvents(data.eventClass), verifyOnlyTable84Contents(tables))
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
          verifyOnlyTable84Contents(tables))
      ),
      18 --> confirm(Sequence.two),
      19 --> send(data.read(Sequence.three)),
      20 --> expectNullResponse(Sequence.three)
    )
  }

}
