// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.common.SBOWithManipulatedControl
import io.stepfunc.dnp3c.dnp3.app.objects.ControlCode
import io.stepfunc.dnp3c.dnp3.app.objects.Group12Var1

object Procedure8_2_1_2_8 extends SBOWithManipulatedControl {

  // a different, defined control code
  override def modify(control: Group12Var1): Group12Var1 = {
    val other = if (control.code == ControlCode.latchOn) ControlCode.latchOff else ControlCode.latchOn
    control.copy(code = other)
  }

}
