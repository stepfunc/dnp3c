// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.conformance.api.options.{ CounterOptions, EventBufferOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.common.{ EventClassData, Validations }
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group22Var1, Group22Var2 }

object Procedure8_6_6_2_3 extends ProcedureDefinition {

  override protected def requiredOptions = Inputs.counter.withEvents :+
    OptionCondition(SupportOptions.counterEventsQueuedPerPoint)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {

    val maxEvents = options.get(EventBufferOptions.maxCounterEvents)
    val eventClass = EventClassData.numbered(options.get(CounterOptions.eventsClass))

    EventBufferSteps.get(
      "Binary Counter Change Events",
      maxEvents,
      eventClass,
      PluginSteps.generateExactCounterChangeEvents,
      Validations.singleEvent("Binary Counter Change Event", Set(Group22Var1, Group22Var2))
    )
  }
}
