// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s84.s843

import io.stepfunc.dnp3c.conformance.s8.helpers.OperationType
import io.stepfunc.dnp3c.conformance.s8.helpers.OutputProcedures.DirectOperateNoAck

object Procedure84321 extends DirectOperateNoAck(OperationType.AnalogOutput) {

  override def id: String = "8.4.3.2.1"

  override def description: String = "Analog Output, Direct Operate, No Acknowledge"
}
