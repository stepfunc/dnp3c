// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s814

import io.stepfunc.dnp3c.conformance.s8.helpers.InputEventProcedures.AllQualifierWithoutTime
import io.stepfunc.dnp3c.conformance.s8.helpers.InputType

object Procedure81426 extends AllQualifierWithoutTime(InputType.BinaryInput) {

  override def id: String = "8.14.2.6"

  override def description: String = "Binary Input Change Without Time, 0x06"
}
