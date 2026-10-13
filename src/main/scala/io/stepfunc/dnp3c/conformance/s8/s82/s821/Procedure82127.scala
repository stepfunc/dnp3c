// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s82.s821

import io.stepfunc.dnp3c.conformance.s8.helpers.SBOWithManipulatedControl
import io.stepfunc.dnp3c.dnp3.app.objects.Group12Var1
import io.stepfunc.dnp3c.protocol.parsing.UInt32LE

object Procedure82127 extends SBOWithManipulatedControl {

  override def modify(control: Group12Var1): Group12Var1 = control.copy(offTime = UInt32LE(control.offTime.value + 1))

}
