// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.conformance.api.options.{ BinaryInputOptions, EventBufferOptions }
import io.stepfunc.dnp3c.conformance.common.{ EventClassData, Validations }
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group2Var1, Group2Var2, Group2Var3 }

object Procedure8_6_6_2_1 extends ProcedureDefinition {

  override protected def requiredOptions = Inputs.binary.withEvents

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {

    val maxEvents = options.get(EventBufferOptions.maxBinaryInputEvents)
    val eventClass = EventClassData.numbered(options.get(BinaryInputOptions.binaryEventsClass))

    EventBufferSteps.get(
      "Binary Input Change Events",
      maxEvents,
      eventClass,
      PluginSteps.generateExactBinaryInputChangeEvents,
      Validations.singleEvent("Binary Input Change Event", Set(Group2Var1, Group2Var2), afterCto = Set(Group2Var3))
    )
  }
}
