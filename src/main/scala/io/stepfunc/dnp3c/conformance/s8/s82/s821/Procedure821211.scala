// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s82.s821

import io.stepfunc.dnp3c.conformance.s8.helpers.OperationType
import io.stepfunc.dnp3c.conformance.s8.helpers.OutputProcedures.SelectRetrySameSequence

object Procedure821211 extends SelectRetrySameSequence(OperationType.BinaryOutput) {

  override def id: String = "8.2.1.2.11"

  override def description: String = "Binary Output, SBO, Same Sequence Number Select Retries"
}
