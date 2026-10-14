// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{
  Index, IndexListOption, IndexOption, IntegerOption, OptionGroup, OptionTypes, RequiredIntegerOption, TestOption
}

object AnalogInputOptions extends OptionGroup {

  val eventsClass: IntegerOption = PointOptions.eventsClass(this, "Analog Input")

  val valueReturnIndex: IndexOption = OptionTypes.index(
    this,
    "valueReturn.index",
    Index.zero,
    "The analog input whose value 3.1r1's 8.19.2.4 changes, and returns"
  )
  val valueReturnDeadband: RequiredIntegerOption = OptionTypes.requiredIntegerAtLeast(
    this,
    "valueReturn.deadband",
    "The configured deadband of the analog input 3.1r1's 8.19.2.4 changes, in the units the DUT reports it in",
    1
  )
  val valueTolerance: IntegerOption = OptionTypes.integer(
    this,
    "valueTolerance",
    0,
    "How far a value the DUT reports for an analog input may be from the value applied to it: 0 for an exact match",
    Some(0),
    None
  )

  val only16Bit: IndexListOption = PointOptions.onlyWidth(this, "", "Analog inputs", 16)
  val only32Bit: IndexListOption = PointOptions.onlyWidth(this, "", "Analog inputs", 32)

  def prefix: String = "analogInput"
  def description: String = "Options related to analog inputs (g30 and g32)"
  def options: List[TestOption] =
    List(eventsClass, valueReturnIndex, valueReturnDeadband, valueTolerance, only16Bit, only32Bit)

}
