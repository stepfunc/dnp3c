// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{
  BooleanOption, Index, IndexListOption, IndexOption, IntegerOption, OptionalIndexOption, OptionGroup, OptionSource,
  OptionTypes, TestOption
}
import io.stepfunc.dnp3c.dnp3.app.objects.{ CommandStatus, Group41Var2 }

object AnalogOutputOptions extends OptionGroup {

  val primary = Options(this, "primary", Index.zero, 10)
  val alternate = Options(this, "alternate", Index.one, 11)
  val uninstalledIndex: IndexOption =
    OptionTypes.index(this, "uninstalled.index", Index.max, "Index not assigned to any analog output point")
  val installedPoints: IndexListOption =
    OptionTypes.indexList(this, "installedPoints", "List of all installed AO indices")
  val maxAnalogOutputsInSingleRequest: IntegerOption = OptionTypes.integer(
    this,
    "maxAnalogOutputsInSingleRequest",
    1,
    "Maximum number of analog outputs that are supported in a single request",
    Some(1),
    None)

  val commands32Bit: BooleanOption = OptionTypes.boolean(
    this,
    "commands32Bit",
    false,
    "Does the DUT accept 32-bit analog output commands (g41v1)? Subset level 3 requires it"
  )
  val statusOnly32Bit: IndexListOption = OptionTypes.indexList(
    this,
    "status.only32Bit",
    "Analog output status points the DUT keeps and reports only as 32-bit values, which 3.1r1's 8.24 accepts in a " +
      "32-bit variation where a 16-bit one is requested. Empty for none",
    Nil
  )
  val statusFlags: IndexListOption = OptionTypes.indexList(
    this,
    "status.changeableFlags",
    "The analog output status flags, by bit number (0 ONLINE to 6 REFERENCE_ERR), that the plugin can change, which " +
      "3.1r1's 10.4.3 changes one at a time. Empty for none",
    Nil
  )
  val statusValueEvents: BooleanOption = OptionTypes.boolean(
    this,
    "status.valueChangeEvents",
    false,
    "Does the DUT generate analog output status events on a significant change of value (3.1r1's 10.4.3)?")
  val statusDeadband: IntegerOption = OptionTypes.integer(
    this,
    "status.deadband",
    1,
    "The change of an analog output status value that generates an event, which 3.1r1's 10.4.3 exceeds",
    Some(1),
    None)
  val commandResetsBase: BooleanOption = OptionTypes.boolean(
    this,
    "status.commandResetsBase",
    false,
    "Does an analog output command reset the base value its status events are declared against (3.1r1's 10.4.3)?")
  val index32Bit: OptionalIndexOption = OptionTypes.optionalIndex(
    this,
    "index32Bit",
    None,
    "Index of an installed 32-bit analog output, which 3.1r1's 8.4.1.2.13 commands; empty if none is installed"
  )

  def prefix: String = "analogOutput"
  def description: String = "Options related to analog outputs (g41)"
  def options: List[TestOption] = uninstalledIndex :: installedPoints :: maxAnalogOutputsInSingleRequest ::
    commands32Bit :: index32Bit :: statusOnly32Bit :: statusFlags :: statusValueEvents :: statusDeadband ::
    commandResetsBase ::
    primary.options ::: alternate.options

  case class Options(group: OptionGroup, id: String, defaultIndex: Index, defaultValue: Int) {

    val index =
      OptionTypes.index(group, s"$id.valid.index", defaultIndex, "Index of a supported Group41Var2", Index.maxOneOctet)
    val value =
      OptionTypes.integer(
        group,
        s"$id.valid.value",
        defaultValue,
        "Value of supported by Group41Var2",
        Some(0),
        Some(32767))

    val options: List[TestOption] = List(index, value)

    def getConfig(src: OptionSource): CommandConfig[Group41Var2] = CommandConfig(
      src.get(index),
      Group41Var2(src.get(value).toShort, CommandStatus.SUCCESS)
    )
  }

}
