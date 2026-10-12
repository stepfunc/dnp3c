// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s822

import io.stepfunc.dnp3c.conformance.s8.helpers.InputEventProcedures.AllQualifierWithRelativeTime
import io.stepfunc.dnp3c.conformance.s8.helpers.InputType

object Procedure822212 extends AllQualifierWithRelativeTime(InputType.DoubleBitBinaryInput) {

  override def id: String = "8.22.2.12"

  override def description: String = "Double-bit Input Change With Relative Time, 0x06"
}
