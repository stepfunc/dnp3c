// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s814

import io.stepfunc.dnp3c.conformance.s8.helpers.InputEventProcedures.RelativeTimeLongInterval
import io.stepfunc.dnp3c.conformance.s8.helpers.InputType

object Procedure814215 extends RelativeTimeLongInterval(InputType.BinaryInput) {

  override def id: String = "8.14.2.15"

  override def description: String = "Binary Input Change with Relative Time, Long Interval"
}
