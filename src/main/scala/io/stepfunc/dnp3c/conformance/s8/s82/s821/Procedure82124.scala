// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s82.s821

import io.stepfunc.dnp3c.conformance.s8.helpers.OperationType
import io.stepfunc.dnp3c.conformance.s8.helpers.OutputProcedures.OperateAfterSelectTimeout

object Procedure82124 extends OperateAfterSelectTimeout(OperationType.BinaryOutput) {

  override def id: String = "8.2.1.2.4"

  override def description: String = "Binary Output, SBO, Execute Issued After Timeout"
}
