// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s85.s854

import io.stepfunc.dnp3c.conformance.s8.helpers.ClassEventProcedures.NoConfirm
import io.stepfunc.dnp3c.conformance.s8.helpers.EventClassData.class3

object Procedure85424 extends NoConfirm(class3) {

  override def id: String = "8.5.4.2.4"

  override def description: String = "Class 3 Data Without Confirm"
}
