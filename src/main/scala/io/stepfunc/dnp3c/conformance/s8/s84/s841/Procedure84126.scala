// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s84.s841

import io.stepfunc.dnp3c.conformance.s8.helpers.OperationType
import io.stepfunc.dnp3c.conformance.s8.helpers.OutputProcedures.SelectTwoByteOperateOneByte

object Procedure84126 extends SelectTwoByteOperateOneByte(OperationType.AnalogOutput) {

  override def id: String = "8.4.1.2.6"

  override def description: String = "Analog Output, SBO, Select 0x28, Execute 0x17"
}
