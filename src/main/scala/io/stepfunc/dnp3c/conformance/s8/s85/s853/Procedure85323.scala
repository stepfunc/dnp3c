// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s85.s853

import io.stepfunc.dnp3c.conformance.s8.helpers.ClassEventProcedures.CountQualifier
import io.stepfunc.dnp3c.conformance.s8.helpers.EventClassData.class2

object Procedure85323 extends CountQualifier(class2, twoByteCount = true) {

  override def id: String = "8.5.3.2.3"

  override def description: String = "Class 2 Data, 0x08"
}
