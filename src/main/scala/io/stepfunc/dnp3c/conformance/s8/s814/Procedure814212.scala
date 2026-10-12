// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s814

import io.stepfunc.dnp3c.conformance.s8.helpers.InputEventProcedures.AllQualifierWithRelativeTime
import io.stepfunc.dnp3c.conformance.s8.helpers.InputType

object Procedure814212 extends AllQualifierWithRelativeTime(InputType.BinaryInput) {

  override def id: String = "8.14.2.12"

  override def description: String = "Binary Input Change With Relative Time, 0x06"
}
