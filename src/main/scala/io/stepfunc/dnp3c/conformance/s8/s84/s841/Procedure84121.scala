// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s84.s841

import io.stepfunc.dnp3c.conformance.s8.helpers.OperationType
import io.stepfunc.dnp3c.conformance.s8.helpers.OutputProcedures.SelectBeforeOperateTwoByte

object Procedure84121 extends SelectBeforeOperateTwoByte(OperationType.AnalogOutput) {

  override def id: String = "8.4.1.2.1"

  override def description: String = "Analog Output, SBO, 0x28"
}
