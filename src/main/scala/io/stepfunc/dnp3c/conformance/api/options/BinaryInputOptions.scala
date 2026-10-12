// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{ IntegerOption, OptionGroup, OptionTypes, TestOption }

object BinaryInputOptions extends OptionGroup {

  val numBinaryEventsGenerated: IntegerOption = OptionTypes.integer(
    this,
    "numBinaryEventsGenerated",
    3,
    "Number of Binary Input Events generated for the 0x07/0x08 count-limited tests (at least 2, so the count limit is exercised).",
    Some(2),
    Some(255)
  )
  val binaryEventsClass: IntegerOption =
    OptionTypes.integer(this, "binaryEventsClass", 1, "Binary Input Events class assignment.", Some(1), Some(3))

  def prefix: String = "binaryInput"
  def description: String = "Options related to binary inputs (g1 and g2)"
  def options: List[TestOption] = List(numBinaryEventsGenerated, binaryEventsClass)

}
