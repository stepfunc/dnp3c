// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s85.s853

import io.stepfunc.dnp3c.conformance.s8.helpers.ClassEventProcedures.NoConfirm
import io.stepfunc.dnp3c.conformance.s8.helpers.EventClassData.class2

object Procedure85324 extends NoConfirm(class2) {

  override def id: String = "8.5.3.2.4"

  override def description: String = "Class 2 Data Without Confirm"
}
