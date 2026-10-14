// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.helpers

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.OutstationOptions
import io.stepfunc.dnp3c.conformance.s8.helpers.EventClassSteps.CountConfirmSteps
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations.Tables
import io.stepfunc.dnp3c.conformance.v31r1.Rules

/**
 * The procedures that 8.5.2, 8.5.3 and 8.5.4 (class 1, 2 and 3 data) each have, written once for any class. A
 * procedure object extends one of these, or a revision's tree creates one, and the tree gives it a number and title.
 */
object ClassEventProcedures {

  // 8.5.2 to 8.5.4 let a Null Response ask for confirmation or not, so 3.1r1's Note 7 doesn't apply to their reads
  private def nullMayAskForConfirmation(steps: Iterable[LabeledStep]): List[LabeledStep] =
    exempting(Set(Rules.confirmOnlyWhenRequired.id))(steps)

  /**
   * Qualifier 0x06. Runs whether or not the DUT supports the class: a DUT without it must answer differently.
   *
   * @param classBitCleared whether the response with all the events must also have the class's IIN1 bit clear, as
   *   3.1r1 requires
   */
  class AllQualifier(data: EventClassData, classBitCleared: Boolean = false) extends ProcedureDefinition {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] =
      nullMayAskForConfirmation(
        EventClassSteps.AllQualifierSteps(data, options.get(data.support), Tables(options), classBitCleared))
  }

  /**
   * Qualifier 0x07, or 0x08 with twoByteCount
   *
   * @param confirms which confirmations are steps of their own, which the revision's numbering follows
   */
  class CountQualifier(
      data: EventClassData,
      twoByteCount: Boolean,
      confirms: CountConfirmSteps = CountConfirmSteps.none)
      extends ProcedureDefinition {
    override protected def requiredOptions: List[OptionCondition] = List(data.support)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = nullMayAskForConfirmation(
      EventClassSteps.CountQualifierSteps(
        data,
        options.get(data.numEventsGenerated),
        twoByteCount,
        Tables(options),
        confirms))
  }

  abstract class NoConfirm(data: EventClassData) extends ProcedureDefinition {
    override protected def requiredOptions: List[OptionCondition] = List(data.support)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] =
      nullMayAskForConfirmation(
        EventClassSteps.NoConfirmSteps(data, options.get(OutstationOptions.appConfirmTimeout), Tables(options)))
  }
}
