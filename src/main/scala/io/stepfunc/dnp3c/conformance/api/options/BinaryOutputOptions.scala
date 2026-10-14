// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{ Index, _ }
import io.stepfunc.dnp3c.dnp3.app.objects.{ CommandStatus, ControlCode, Group12Var1 }
import io.stepfunc.dnp3c.protocol.parsing.UInt32LE

object BinaryOutputOptions extends OptionGroup {

  private def validCROBCodes = ControlCode.list.map(_.value).toSet

  val primary = Options(this, "primary", Index.zero)
  val alternate = Options(this, "alternate", Index.one)
  val installedPoints: IndexListOption = PointOptions.installedPoints(this, "BO")
  val statusFlags: IndexListOption =
    PointOptions.changeableFlags(this, "binary output", "4 LOCAL_FORCED", "10.3.3")
  val statusStateEvents: BooleanOption = OptionTypes.boolean(
    this,
    "status.stateChangeEvents",
    false,
    "Does the DUT generate binary output status events on a change of state (3.1r1's 10.3.3)?")
  val uninstalledIndex: IndexOption = PointOptions.uninstalledIndex(this, "binary output")
  val maxPointsInSingleRequest: IntegerOption = PointOptions.maxPointsInSingleRequest(this, "binary output")
  val latchOnOffPoint = ControlCodePointOptions(
    this,
    "latchOnOffPoint",
    Index.one,
    "Optional control point supporting complementary function codes Latch On/NUL (0x03) and Latch Off/NUL (0x04).")
  val pulseOnOffPoint = ControlCodePointOptions(
    this,
    "pulseOnOffPoint",
    Index.one,
    "Optional control point supporting complementary function codes Pulse On/Close (0x41) and Pulse On/Trip (0x81).")
  val latchAndPulsePoint = ControlCodePointOptions(
    this,
    "latchAndPulsePoint",
    Index.one,
    "Optional control point supporting complementary function codes 0x03, 0x04, 0x41 and 0x81.")
  val singleFunctionPulseOnPoint = ControlCodePointOptions(
    this,
    "singleFunctionPulseOnPoint",
    Index.one,
    "Optional single function point supported function code Pulse On/NUL (0x01).")
  val singleFunctionLatchOnPoint = ControlCodePointOptions(
    this,
    "singleFunctionLatchOnPoint",
    Index.one,
    "Optional single function point supported function code Latch On/NUL (0x03).")
  val singleFunctionLatchOffPoint = ControlCodePointOptions(
    this,
    "singleFunctionLatchOffPoint",
    Index.one,
    "Optional single function point supported function code Latch Off/NUL (0x04).")
  val singleFunctionPulseOnClosePoint = ControlCodePointOptions(
    this,
    "singleFunctionPulseOnClosePoint",
    Index.one,
    "Optional single function point supported function code Pulse On/Close (0x41).")
  val singleFunctionPulseOnTripPoint = ControlCodePointOptions(
    this,
    "singleFunctionPulseOnTripPoint",
    Index.one,
    "Optional single function point supported function code Pulse On/Trip (0x81).")

  def prefix: String = "binaryOutput"
  def description: String = "Options related to binary outputs (g10 and g12)"
  def options: List[TestOption] = uninstalledIndex :: maxPointsInSingleRequest :: installedPoints ::
    statusFlags :: statusStateEvents ::
    primary.options ::: alternate.options :::
    latchOnOffPoint.options ::: pulseOnOffPoint.options ::: latchAndPulsePoint.options :::
    singleFunctionPulseOnPoint.options ::: singleFunctionLatchOnPoint.options :::
    singleFunctionLatchOffPoint.options ::: singleFunctionPulseOnClosePoint.options :::
    singleFunctionPulseOnTripPoint.options

  case class Options(group: OptionGroup, id: String, defaultIndex: Index) {

    val index: IndexOption =
      OptionTypes.index(group, s"$id.valid.index", defaultIndex, "Index of a supported Group12Var1", Index.maxOneOctet)
    private val function = OptionTypes.hexByteInSet(
      group,
      s"$id.valid.function",
      ControlCode.latchOn.value,
      "Valid control function in hexadecimal",
      validCROBCodes)
    private val crob = CrobFields(group, s"$id.valid")

    val options: List[TestOption] = List(index, function) ::: crob.options

    def getConfig(src: OptionSource): CommandConfig[Group12Var1] = {
      CommandConfig(
        src.get(index),
        Group12Var1(
          ControlCode(src.get(function)),
          src.get(crob.count).toByte,
          UInt32LE(src.get(crob.onTime)),
          UInt32LE(src.get(crob.offTime)),
          CommandStatus.SUCCESS)
      )
    }
  }

  case class ControlCodePointOptions(group: OptionGroup, id: String, defaultIndex: Index, description: String) {
    val index = OptionTypes.optionalIndex(group, s"$id.index", Some(defaultIndex), description, Index.maxOneOctet)
    private val crob = CrobFields(group, id)

    val options: List[TestOption] = index :: crob.options

    def isPresent(src: OptionSource): Boolean = {
      src.get(index) match {
        case Some(_) => true
        case None => false
      }
    }

    def getConfig(src: OptionSource, function: ControlCode): Option[CommandConfig[Group12Var1]] = {
      src.get(index) match {
        case Some(idx) => Some(CommandConfig(
            idx,
            Group12Var1(
              function,
              src.get(crob.count).toByte,
              UInt32LE(src.get(crob.onTime)),
              UInt32LE(src.get(crob.offTime)),
              CommandStatus.SUCCESS)
          ))
        case None => None
      }
    }
  }

  // a CROB's count and on and off times, under the key prefix
  private case class CrobFields(group: OptionGroup, prefix: String) {
    val count: IntegerOption =
      OptionTypes.integer(group, s"$prefix.count", 1, "Valid count for Group12Var1", Some(1), Some(255))
    val onTime: IntegerOption =
      OptionTypes.integer(group, s"$prefix.onTime.ms", 100, "On time in ms", Some(0), Some(65535))
    val offTime: IntegerOption =
      OptionTypes.integer(group, s"$prefix.offTime.ms", 100, "Off time in ms", Some(0), Some(65535))

    val options: List[TestOption] = List(count, onTime, offTime)
  }

}
