// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s86.s866

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.conformance.api.options.{ CounterOptions, EventBufferOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.s8.helpers.{ EventBufferSteps, EventClassData, Validations }
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group22Var1, Group22Var2 }

object Procedure86623 extends TestProcedure {

  override def id: String = "8.6.6.2.3"

  override def description: String = "Buffer Overflow, Binary Counter Change Event Buffers"

  override protected def requiredOptions = Inputs.counter.withEvents :+
    OptionCondition(SupportOptions.counterEventsQueuedPerPoint)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {

    val maxEvents = options.get(EventBufferOptions.maxCounterEvents)
    val eventClass = EventClassData.numbered(options.get(CounterOptions.counterEventsClass))

    EventBufferSteps.get(
      "Binary Counter Change Events",
      maxEvents,
      eventClass,
      PluginSteps.generateExactCounterChangeEvents,
      Validations.singleEvent("Binary Counter Change Event", Set(Group22Var1, Group22Var2))
    )
  }
}
