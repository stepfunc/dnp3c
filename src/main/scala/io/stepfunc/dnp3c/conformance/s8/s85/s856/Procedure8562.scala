// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s85.s856

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions
import io.stepfunc.dnp3c.conformance.s8.helpers.EventClassData
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.{ AutoConfirm, MessageValidation, Requests }

object Procedure8562 extends TestProcedure {

  override def id: String = "8.5.6.2"

  override def description: String = "Test Procedure"

  private def verifyContainsOnly(clazz: EventClass): MessageValidation = {
    PluginValidations.verifyLatestClassEvents(clazz)
  }

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {

    val sequence = MutableSequence.zero
    def verifyEventsWithConfirm(clazz: EventClass): MessageValidation = {
      verifyContainsOnly(clazz) && verifyOnlyTable84Contents(options.get(SupportOptions.Inputs.doubleBit.points)) &&
      verifyConfirmRequested
    }

    // A device need not support every event class (8.5.2-8.5.4); for a class it doesn't support, expect Null Responses
    def classSteps(first: Int, data: EventClassData): List[LabeledStep] =
      if (options.get(data.support)) List(
        first --> send(data.read(sequence.increment())),
        (first + 1 to first + 3) -->
          expectSingleResponse(sequence, AutoConfirm.Yes, verifyEventsWithConfirm(data.eventClass)),
        first + 4 --> send(data.read(sequence.increment())),
        first + 5 --> expectNullResponse(sequence)
      )
      else List(
        first --> send(data.read(sequence.increment())),
        (first + 1 to first + 5) -->
          describe(s"${data.eventClass} events are not supported") + expectNullResponse(sequence)
      )

    List(
      1 --> send(Requests.readClass123(sequence)),
      (2 to 3) -->
        expectAtLeastOneResponse(
          sequence,
          verifyOnlyTable84Contents(options.get(SupportOptions.Inputs.doubleBit.points)) &&
            verifyConfirmRequestedIfNotNull),
      (4 to 6) --> PluginSteps.generateClassEvents(EventClass.All)
    ) ++
      classSteps(7, EventClassData.class1) ++
      classSteps(13, EventClassData.class2) ++
      classSteps(19, EventClassData.class3) ++
      List(
        25 --> send(Requests.readClass123(sequence.increment())),
        26 --> expectNullResponse(sequence)
      )
  }
}
