// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s82.s822

import io.stepfunc.dnp3c.conformance.s8.helpers.OperationType
import io.stepfunc.dnp3c.conformance.s8.helpers.OutputProcedures.DirectOperate

object Procedure82221 extends DirectOperate(OperationType.BinaryOutput) {

  override def id: String = "8.2.2.2.1"

  override def description: String = "Binary Output, Direct Operate"
}
