// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{ IntegerOption, OptionGroup, OptionTypes, TestOption }

object EventBufferOptions extends OptionGroup {

  val maxBinaryInputEvents: IntegerOption =
    OptionTypes.integer(this, "maxBinaryInputEvents", 10, "Maximum number of binary input events", Some(1), None)
  val maxAnalogInputEvents: IntegerOption =
    OptionTypes.integer(this, "maxAnalogInputEvents", 10, "Maximum number of analog input events", Some(1), None)
  val maxCounterEvents: IntegerOption =
    OptionTypes.integer(this, "maxCounterEvents", 10, "Maximum number of binary counter events", Some(1), None)
  val maxDoubleBitBinaryInputEvents: IntegerOption = OptionTypes.integer(
    this,
    "maxDoubleBitBinaryInputEvents",
    10,
    "Maximum number of double-bit binary input events",
    Some(1),
    None)

  override def prefix: String = "event.buffer"

  override def description: String = "Event buffer sizes"

  override def options: List[TestOption] =
    List(maxBinaryInputEvents, maxAnalogInputEvents, maxCounterEvents, maxDoubleBitBinaryInputEvents)
}
