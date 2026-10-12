// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s85.s852

import io.stepfunc.dnp3c.conformance.s8.helpers.ClassEventProcedures.CountQualifier
import io.stepfunc.dnp3c.conformance.s8.helpers.EventClassData.class1

object Procedure85222 extends CountQualifier(class1, twoByteCount = false) {

  override def id: String = "8.5.2.2.2"

  override def description: String = "Class 1 Data, 0x07"
}
