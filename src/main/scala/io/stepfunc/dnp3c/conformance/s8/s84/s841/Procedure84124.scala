// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s84.s841

import io.stepfunc.dnp3c.conformance.s8.helpers.OperationType
import io.stepfunc.dnp3c.conformance.s8.helpers.OutputProcedures.OperateAfterSelectTimeout

object Procedure84124 extends OperateAfterSelectTimeout(OperationType.AnalogOutput) {

  override def id: String = "8.4.1.2.4"

  override def description: String = "Analog Output, SBO, Execute Issued After Time-out"
}
