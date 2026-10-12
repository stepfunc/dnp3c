// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s86.s866

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.conformance.api.options.{ AnalogInputOptions, EventBufferOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.s8.helpers.{ EventBufferSteps, EventClassData, Validations }
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group32Var1, Group32Var2, Group32Var3, Group32Var4 }

object Procedure86622 extends TestProcedure {

  override def id: String = "8.6.6.2.2"

  override def description: String = "Buffer Overflow, Analog Input Change Event Buffers"

  override protected def requiredOptions = Inputs.analog.withEvents :+
    OptionCondition(SupportOptions.analogEventsQueuedPerPoint)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {

    val maxEvents = options.get(EventBufferOptions.maxAnalogInputEvents)
    val eventClass = EventClassData.numbered(options.get(AnalogInputOptions.analogInputEventsClass))

    EventBufferSteps.get(
      "Analog Input Change Events",
      maxEvents,
      eventClass,
      PluginSteps.generateExactAnalogInputChangeEvents,
      Validations.singleEvent("Analog Input Change Event", Set(Group32Var1, Group32Var2, Group32Var3, Group32Var4))
    )
  }
}
