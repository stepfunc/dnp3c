// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{ IntegerOption, OptionGroup, OptionTypes, TestOption }

object AnalogInputOptions extends OptionGroup {

  val analogInputEventsClass: IntegerOption =
    OptionTypes.integer(this, "analogInputEventsClass", 1, "Analog Input Events class assignment.", Some(1), Some(3))

  def prefix: String = "analogInput"
  def description: String = "Options related to analog inputs (g30 and g32)"
  def options: List[TestOption] = List(analogInputEventsClass)

}
