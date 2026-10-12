// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s84.s841

import io.stepfunc.dnp3c.conformance.s8.helpers.OperationType
import io.stepfunc.dnp3c.conformance.s8.helpers.OutputProcedures.SelectRetrySameSequence

object Procedure84128 extends SelectRetrySameSequence(OperationType.AnalogOutput) {

  override def id: String = "8.4.1.2.8"

  override def description: String = "Analog Output, SBO, Same Sequence Number Select Retries"
}
