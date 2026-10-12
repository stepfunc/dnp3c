// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s814

import io.stepfunc.dnp3c.conformance.s8.helpers.InputEventProcedures.AllQualifierWithTime
import io.stepfunc.dnp3c.conformance.s8.helpers.InputType

object Procedure81429 extends AllQualifierWithTime(InputType.BinaryInput) {

  override def id: String = "8.14.2.9"

  override def description: String = "Binary Input Change With Time, 0x06"
}
