// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s82.s823

import io.stepfunc.dnp3c.conformance.s8.helpers.OperationType
import io.stepfunc.dnp3c.conformance.s8.helpers.OutputProcedures.DirectOperateNoAckConfigurableDevice

object Procedure82323 extends DirectOperateNoAckConfigurableDevice(OperationType.BinaryOutput) {

  override def id: String = "8.2.3.2.3"

  override def description: String = "Binary Output, Direct Operate, No Acknowledge, Configurable Device"
}
