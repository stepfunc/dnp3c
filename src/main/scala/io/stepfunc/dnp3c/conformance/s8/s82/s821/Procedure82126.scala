// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s82.s821

import io.stepfunc.dnp3c.conformance.s8.helpers.SBOWithManipulatedControl
import io.stepfunc.dnp3c.dnp3.app.objects.Group12Var1
import io.stepfunc.dnp3c.protocol.parsing.UInt32LE

object Procedure82126 extends SBOWithManipulatedControl {

  def id = "8.2.1.2.6"

  def description = "Binary Output, SBO, Execute On Time does not match Select On Time"

  override def modify(control: Group12Var1): Group12Var1 = control.copy(onTime = UInt32LE(control.onTime.value + 1))

}
