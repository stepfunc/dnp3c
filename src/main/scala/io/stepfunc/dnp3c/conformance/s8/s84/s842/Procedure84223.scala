// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s84.s842

import io.stepfunc.dnp3c.conformance.s8.helpers.OperationType
import io.stepfunc.dnp3c.conformance.s8.helpers.OutputProcedures.DirectOperateConfigurableDevice

object Procedure84223 extends DirectOperateConfigurableDevice(OperationType.AnalogOutput) {

  override def id: String = "8.4.2.2.3"

  override def description: String = "Analog Output, Direct Operate, Configurable Device"
}
