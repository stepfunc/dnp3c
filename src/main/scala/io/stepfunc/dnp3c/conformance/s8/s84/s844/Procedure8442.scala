// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s84.s844

import io.stepfunc.dnp3c.conformance.s8.helpers.OperationType
import io.stepfunc.dnp3c.conformance.s8.helpers.OutputProcedures.MultipleObjects

object Procedure8442 extends MultipleObjects(OperationType.AnalogOutput) {

  override def id: String = "8.4.4.2"

  override def description: String = "Test Procedure"
}
