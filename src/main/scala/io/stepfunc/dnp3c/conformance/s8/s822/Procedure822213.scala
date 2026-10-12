// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s822

import io.stepfunc.dnp3c.conformance.s8.helpers.InputEventProcedures.CountQualifierWithRelativeTime
import io.stepfunc.dnp3c.conformance.s8.helpers.InputType

object Procedure822213 extends CountQualifierWithRelativeTime(InputType.DoubleBitBinaryInput, twoByteCount = false) {

  override def id: String = "8.22.2.13"

  override def description: String = "Double-bit Input Change With Relative Time, 0x07"
}
