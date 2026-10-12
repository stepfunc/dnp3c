// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s814

import io.stepfunc.dnp3c.conformance.s8.helpers.InputEventProcedures.CountQualifierWithRelativeTime
import io.stepfunc.dnp3c.conformance.s8.helpers.InputType

object Procedure814214 extends CountQualifierWithRelativeTime(InputType.BinaryInput, twoByteCount = true) {

  override def id: String = "8.14.2.14"

  override def description: String = "Binary Input Change With Relative Time, 0x08"
}
