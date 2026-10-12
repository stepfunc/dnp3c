// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s814

import io.stepfunc.dnp3c.conformance.s8.helpers.InputEventProcedures.CountQualifier
import io.stepfunc.dnp3c.conformance.s8.helpers.InputType

object Procedure81423 extends CountQualifier(InputType.BinaryInput, twoByteCount = false) {

  override def id: String = "8.14.2.3"

  override def description: String = "Binary Input Change, 0x07"
}
