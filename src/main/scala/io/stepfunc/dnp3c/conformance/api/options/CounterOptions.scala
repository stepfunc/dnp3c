// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{ IndexListOption, IntegerOption, OptionGroup, OptionTypes, TestOption }

object CounterOptions extends OptionGroup {

  val numCounterEventsGenerated: IntegerOption = OptionTypes.integer(
    this,
    "numCounterEventsGenerated",
    3,
    "Number of Counter Events generated for the 0x07/0x08 count-limited tests (at least 2, so the count limit is exercised).",
    Some(2),
    Some(255)
  )
  val counterEventsClass: IntegerOption =
    OptionTypes.integer(this, "counterEventsClass", 1, "Counter Events class assignment.", Some(1), Some(3))

  val alwaysCounting: IndexListOption = OptionTypes.indexList(
    this,
    "alwaysCounting",
    "Counters that can't be kept from counting, so a freeze and clear may not find them at zero: 3.1r1's 8.16.2.2.4 " +
      "and 8.16.2.2.6 don't expect their frozen values to be zero after the second freeze. Empty for none",
    Nil
  )

  def prefix: String = "counter"
  def description: String = "Options related to counters (g20 and g22)"
  def options: List[TestOption] = List(numCounterEventsGenerated, counterEventsClass, alwaysCounting)

}
