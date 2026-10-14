// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.conformance.common.ConfigurableInputProcedure
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.app.objects.Group3Var0

object Procedure8_21_2_2 extends ConfigurableInputProcedure(
      "double-bit inputs",
      PluginSteps.uninstallDoubleBitBinaryInputs,
      Group3Var0,
      Sequence.zero) {

  override protected def requiredOptions: List[OptionCondition] = Inputs.doubleBit.withDisabling
}
