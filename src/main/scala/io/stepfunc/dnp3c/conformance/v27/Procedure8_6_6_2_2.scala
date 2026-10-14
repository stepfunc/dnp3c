// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.conformance.api.options.{ AnalogInputOptions, EventBufferOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.common.{ EventClassData, Validations }
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group32Var1, Group32Var2, Group32Var3, Group32Var4 }

object Procedure8_6_6_2_2 extends ProcedureDefinition {

  override protected def requiredOptions = Inputs.analog.withEvents :+
    OptionCondition(SupportOptions.analogInputEventsQueuedPerPoint)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {

    val maxEvents = options.get(EventBufferOptions.maxAnalogInputEvents)
    val eventClass = EventClassData.numbered(options.get(AnalogInputOptions.eventsClass))

    EventBufferSteps.get(
      "Analog Input Change Events",
      maxEvents,
      eventClass,
      PluginSteps.generateExactAnalogInputChangeEvents,
      Validations.singleEvent("Analog Input Change Event", Set(Group32Var1, Group32Var2, Group32Var3, Group32Var4))
    )
  }
}
