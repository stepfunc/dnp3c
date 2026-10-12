// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.helpers

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ OutstationOptions, SupportOptions }

/**
 * The procedures that 8.5.2, 8.5.3 and 8.5.4 (class 1, 2 and 3 data) each have, written once for any class. A
 * procedure object extends one of these with its own id and description.
 */
object ClassEventProcedures {

  private def doubleBitPoints(options: OptionSource): Boolean = options.get(SupportOptions.Inputs.doubleBit.points)

  // runs whether or not the DUT supports the class: a DUT without it must answer differently
  abstract class AllQualifier(data: EventClassData) extends TestProcedure {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] =
      EventClassSteps.AllQualifierSteps(data, options.get(data.support), doubleBitPoints(options))
  }

  // qualifier 0x07, or 0x08 with twoByteCount
  abstract class CountQualifier(data: EventClassData, twoByteCount: Boolean) extends TestProcedure {
    override protected def requiredOptions: List[OptionCondition] = List(data.support)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = EventClassSteps.CountQualifierSteps(
      data,
      options.get(data.numEventsGenerated),
      twoByteCount,
      doubleBitPoints(options))
  }

  abstract class NoConfirm(data: EventClassData) extends TestProcedure {
    override protected def requiredOptions: List[OptionCondition] = List(data.support)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = EventClassSteps.NoConfirmSteps(
      data,
      options.get(OutstationOptions.appConfirmTimeout),
      doubleBitPoints(options))
  }
}
