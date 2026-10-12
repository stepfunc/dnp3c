// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s822

import io.stepfunc.dnp3c.conformance.s8.helpers.InputEventProcedures.RelativeTimeLongInterval
import io.stepfunc.dnp3c.conformance.s8.helpers.InputType

object Procedure822215 extends RelativeTimeLongInterval(InputType.DoubleBitBinaryInput) {

  override def id: String = "8.22.2.15"

  override def description: String = "Double-bit Input Change with Relative Time, Long Interval"
}
