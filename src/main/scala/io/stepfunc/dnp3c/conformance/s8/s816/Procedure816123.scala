// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s816

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.conformance.s8.helpers.ConfigurableInputProcedure
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.app.objects.Group20Var0

object Procedure816123
    extends ConfigurableInputProcedure("counters", PluginSteps.uninstallCounterInputs, Group20Var0, Sequence.one) {

  override protected def requiredOptions: List[OptionCondition] = Inputs.counter.withDisabling
}
