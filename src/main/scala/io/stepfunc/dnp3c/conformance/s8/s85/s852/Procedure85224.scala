// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s85.s852

import io.stepfunc.dnp3c.conformance.s8.helpers.ClassEventProcedures.NoConfirm
import io.stepfunc.dnp3c.conformance.s8.helpers.EventClassData.class1

object Procedure85224 extends NoConfirm(class1) {

  override def id: String = "8.5.2.2.4"

  override def description: String = "Class 1 Data Without Confirm"
}
