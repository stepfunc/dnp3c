// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{ IntegerOption, OptionGroup, OptionTypes, TestOption }

object EventBufferOptions extends OptionGroup {

  private def maxEvents(points: String, events: String): IntegerOption =
    OptionTypes.integer(this, s"max${points}Events", 10, s"Maximum number of $events events", Some(1), None)

  val maxBinaryInputEvents: IntegerOption = maxEvents("BinaryInput", "binary input")
  val maxAnalogInputEvents: IntegerOption = maxEvents("AnalogInput", "analog input")
  val maxCounterEvents: IntegerOption = maxEvents("Counter", "counter")
  val maxDoubleBitInputEvents: IntegerOption = maxEvents("DoubleBitInput", "double-bit input")

  override def prefix: String = "event.buffer"

  override def description: String = "Event buffer sizes"

  override def options: List[TestOption] =
    List(maxBinaryInputEvents, maxAnalogInputEvents, maxCounterEvents, maxDoubleBitInputEvents)
}
