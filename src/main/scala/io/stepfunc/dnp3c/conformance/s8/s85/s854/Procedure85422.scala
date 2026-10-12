// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s85.s854

import io.stepfunc.dnp3c.conformance.s8.helpers.ClassEventProcedures.CountQualifier
import io.stepfunc.dnp3c.conformance.s8.helpers.EventClassData.class3

object Procedure85422 extends CountQualifier(class3, twoByteCount = false) {

  override def id: String = "8.5.4.2.2"

  override def description: String = "Class 3 Data, 0x07"
}
