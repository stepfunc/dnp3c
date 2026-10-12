// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s822

import io.stepfunc.dnp3c.conformance.s8.helpers.InputEventProcedures.CountQualifier
import io.stepfunc.dnp3c.conformance.s8.helpers.InputType

object Procedure82224 extends CountQualifier(InputType.DoubleBitBinaryInput, twoByteCount = true) {

  override def id: String = "8.22.2.4"

  override def description: String = "Double-bit Input Change, 0x08"
}
