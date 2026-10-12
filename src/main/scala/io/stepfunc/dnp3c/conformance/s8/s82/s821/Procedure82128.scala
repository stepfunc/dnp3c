// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s82.s821

import io.stepfunc.dnp3c.conformance.s8.helpers.SBOWithManipulatedControl
import io.stepfunc.dnp3c.dnp3.app.objects.ControlCode
import io.stepfunc.dnp3c.dnp3.app.objects.Group12Var1

object Procedure82128 extends SBOWithManipulatedControl {

  def id = "8.2.1.2.8"

  def description = "Binary Output, SBO, Select with supported Code, Execute with different Code"

  // a different, defined control code
  override def modify(control: Group12Var1): Group12Var1 = {
    val other = if (control.code == ControlCode.latchOn) ControlCode.latchOff else ControlCode.latchOn
    control.copy(code = other)
  }

}
