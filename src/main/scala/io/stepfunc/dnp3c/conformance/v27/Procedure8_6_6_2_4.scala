// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.conformance.api.options.{ DoubleBitInputOptions, EventBufferOptions }
import io.stepfunc.dnp3c.conformance.common.{ EventClassData, Validations }
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group4Var1, Group4Var2, Group4Var3 }

object Procedure8_6_6_2_4 extends ProcedureDefinition {

  override protected def requiredOptions = Inputs.doubleBit.withEvents

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {

    val maxEvents = options.get(EventBufferOptions.maxDoubleBitInputEvents)
    val eventClass = EventClassData.numbered(options.get(DoubleBitInputOptions.eventsClass))

    EventBufferSteps.get(
      "Double-bit Input Change Events",
      maxEvents,
      eventClass,
      PluginSteps.generateExactDoubleBitBinaryInputChangeEvents,
      Validations.singleEvent("Double-bit Input Change Event", Set(Group4Var1, Group4Var2), afterCto = Set(Group4Var3))
    )
  }
}
