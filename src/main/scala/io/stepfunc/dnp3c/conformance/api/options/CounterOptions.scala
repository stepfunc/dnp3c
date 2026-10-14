// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{ IndexListOption, IntegerOption, OptionGroup, OptionTypes, TestOption }

object CounterOptions extends OptionGroup {

  val numEventsGenerated: IntegerOption = PointOptions.numEventsGenerated(this, "Counter")
  val eventsClass: IntegerOption = PointOptions.eventsClass(this, "Counter")

  val alwaysCounting: IndexListOption = OptionTypes.indexList(
    this,
    "alwaysCounting",
    "Counters that can't be kept from counting, so a freeze and clear may not find them at zero: 3.1r1's 8.16.2.2.4 " +
      "and 8.16.2.2.6 don't expect their frozen values to be zero after the second freeze. Empty for none",
    Nil
  )

  val only16Bit: IndexListOption = PointOptions.onlyWidth(this, "", "Counters", 16)
  val only32Bit: IndexListOption = PointOptions.onlyWidth(this, "", "Counters", 32)
  val frozenOnly16Bit: IndexListOption = PointOptions.onlyWidth(this, "frozen.", "Frozen counters", 16)
  val frozenOnly32Bit: IndexListOption = PointOptions.onlyWidth(this, "frozen.", "Frozen counters", 32)

  def prefix: String = "counter"
  def description: String = "Options related to counters (g20 and g22)"
  def options: List[TestOption] = List(
    numEventsGenerated,
    eventsClass,
    alwaysCounting,
    only16Bit,
    only32Bit,
    frozenOnly16Bit,
    frozenOnly32Bit)

}
