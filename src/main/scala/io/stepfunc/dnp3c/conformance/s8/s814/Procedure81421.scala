// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s814

import io.stepfunc.dnp3c.conformance.s8.helpers.InputEventProcedures.NoEvents
import io.stepfunc.dnp3c.conformance.s8.helpers.InputType

object Procedure81421 extends NoEvents(InputType.BinaryInput) {

  override def id: String = "8.14.2.1"

  override def description: String = "Device does not support Binary Input Changes"
}
