// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{ IntegerOption, OptionGroup, OptionTypes, TestOption, RequiredIntegerOption }

object DoubleBitInputOptions extends OptionGroup {
  val numDoubleBitEventsGenerated: IntegerOption = OptionTypes.integer(
    this,
    "numDoubleBitEventsGenerated",
    3,
    "Number of Double-bit Input Events generated for the 0x07/0x08 count-limited tests (at least 2, so the count limit is exercised).",
    Some(2),
    Some(255)
  )
  val doubleBitEventsClass: IntegerOption =
    OptionTypes.integer(this, "doubleBitEventsClass", 1, "Double-bit Input Events class assignment.", Some(1), Some(3))

  def prefix: String = "doubleBitInput"
  def description: String = "Options related to Double-bit inputs (g3 and g4)"
  val defaultEventVariation: RequiredIntegerOption = OptionTypes.requiredInteger(
    this,
    "defaultEventVariation",
    "The Device Profile's default variation for double-bit input change events (g4): what 3.1r1 checks a read of g4 variation " +
      "0, or of a class, against",
    List(1, 2, 3)
  )

  def options: List[TestOption] = List(numDoubleBitEventsGenerated, doubleBitEventsClass, defaultEventVariation)
}
