// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s813

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.conformance.s8.helpers.ConfigurableInputProcedure
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.app.objects.Group1Var0

object Procedure81323
    extends ConfigurableInputProcedure("binary inputs", PluginSteps.uninstallBinaryInputs, Group1Var0, Sequence.one) {

  override def id: String = "8.13.2.3"

  override def description: String = "Device supports Binary Inputs, Configurable Device"

  override protected def requiredOptions: List[OptionCondition] = Inputs.binary.withDisabling
}
