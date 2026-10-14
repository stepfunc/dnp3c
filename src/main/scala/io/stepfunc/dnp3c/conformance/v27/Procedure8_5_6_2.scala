// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.common.EventClassData
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.Validations._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.{ AutoConfirm, MessageValidation, Requests }

/**
 * 8.5.6's Class Assignment Verification
 *
 * @param confirmSteps whether each confirmation is a step of its own, as in 3.1r1 (steps 4, 12, 19 and 26); 2.7 numbers
 *   none, though the master confirms every response that asks
 */
class ClassAssignmentVerification(confirmSteps: Boolean) extends ProcedureDefinition {

  private def verifyContainsOnly(clazz: EventClass): MessageValidation = {
    PluginValidations.verifyLatestClassEvents(clazz)
  }

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    val c = if (confirmSteps) 1 else 0
    val sequence = MutableSequence.zero
    val tables = Tables(options)

    def verifyEventsWithConfirm(clazz: EventClass): MessageValidation = {
      verifyContainsOnly(clazz) && verifyOnlyTable84Contents(tables) && verifyConfirmRequested
    }

    // A device need not support every event class (8.5.2-8.5.4); for a class it doesn't support, expect Null Responses
    def classSteps(first: Int, data: EventClassData): List[LabeledStep] =
      if (options.get(data.support)) List(
        first --> send(data.read(sequence.increment())),
        (first + 1 to first + 3 + c) -->
          expectSingleResponse(sequence, AutoConfirm.Yes, verifyEventsWithConfirm(data.eventClass)),
        first + 4 + c --> send(data.read(sequence.increment())),
        first + 5 + c --> expectNullResponse(sequence)
      )
      else List(
        first --> send(data.read(sequence.increment())),
        (first + 1 to first + 5 + c) -->
          describe(s"${data.eventClass} events are not supported") + expectNullResponse(sequence)
      )

    List(
      1 --> send(Requests.readClass123(sequence)),
      (2 to 3 + c) -->
        expectAtLeastOneResponse(sequence, verifyOnlyTable84Contents(tables) && verifyConfirmRequestedIfNotNull),
      (4 + c to 6 + c) --> PluginSteps.generateClassEvents(EventClass.All)
    ) ++
      classSteps(7 + c, EventClassData.class1) ++
      classSteps(13 + 2 * c, EventClassData.class2) ++
      classSteps(19 + 3 * c, EventClassData.class3) ++
      List(
        25 + 4 * c --> send(Requests.readClass123(sequence.increment())),
        26 + 4 * c --> expectNullResponse(sequence)
      )
  }
}

object Procedure8_5_6_2 extends ClassAssignmentVerification(confirmSteps = false)
