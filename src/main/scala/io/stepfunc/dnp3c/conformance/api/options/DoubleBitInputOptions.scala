// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{ IntegerOption, OptionGroup, RequiredIntegerOption, TestOption }

object DoubleBitInputOptions extends OptionGroup {

  def prefix: String = "doubleBitInput"
  def description: String = "Options related to Double-bit inputs (g3 and g4)"

  val numEventsGenerated: IntegerOption = PointOptions.numEventsGenerated(this, "Double-bit Input")
  val eventsClass: IntegerOption = PointOptions.eventsClass(this, "Double-bit Input")
  val defaultEventVariation: RequiredIntegerOption = PointOptions.defaultEventVariation(this, "double-bit input", 4)

  def options: List[TestOption] = List(numEventsGenerated, eventsClass, defaultEventVariation)
}
