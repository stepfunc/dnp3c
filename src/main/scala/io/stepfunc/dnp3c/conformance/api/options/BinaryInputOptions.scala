// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{ IntegerOption, OptionGroup, RequiredIntegerOption, TestOption }

object BinaryInputOptions extends OptionGroup {

  def prefix: String = "binaryInput"
  def description: String = "Options related to binary inputs (g1 and g2)"

  val numEventsGenerated: IntegerOption = PointOptions.numEventsGenerated(this, "Binary Input")
  val eventsClass: IntegerOption = PointOptions.eventsClass(this, "Binary Input")
  val defaultEventVariation: RequiredIntegerOption = PointOptions.defaultEventVariation(this, "binary input", 2)

  def options: List[TestOption] = List(numEventsGenerated, eventsClass, defaultEventVariation)
}
