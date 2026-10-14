// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.common.SBOWithManipulatedControl
import io.stepfunc.dnp3c.dnp3.app.objects.Group12Var1
import io.stepfunc.dnp3c.protocol.parsing.UInt32LE

object Procedure8_2_1_2_6 extends SBOWithManipulatedControl {

  override def modify(control: Group12Var1): Group12Var1 = control.copy(onTime = UInt32LE(control.onTime.value + 1))

}
