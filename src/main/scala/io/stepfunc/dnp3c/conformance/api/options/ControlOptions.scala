// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{ Index, _ }
import io.stepfunc.dnp3c.dnp3.app.objects.{ CommandStatus, ControlCode, Group12Var1 }
import io.stepfunc.dnp3c.protocol.parsing.UInt32LE

object ControlOptions extends OptionGroup {

  private def validCROBCodes = ControlCode.list.map(_.value).toSet

  val primary = Options(this, "primary", Index.zero)
  val alternate = Options(this, "alternate", Index.one)
  val installedPoints: IndexListOption =
    OptionTypes.indexList(this, "installedPoints", "List of all installed BO indices")
  val uninstalledIndex: IndexOption =
    OptionTypes.index(this, "uninstalled.index", Index.max, "Index not assigned to any control point")
  val maxControlPointsInSingleRequest: IntegerOption = OptionTypes.integer(
    this,
    "maxControlPointsInSingleRequest",
    1,
    "Maximum number of control points that are supported in a single request",
    Some(1),
    None)
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

  def prefix: String = "control"
  def description: String = "Options related to controls (g12v1)"
  def options: List[TestOption] = uninstalledIndex :: maxControlPointsInSingleRequest :: installedPoints ::
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
    private val count =
      OptionTypes.integer(group, s"$id.valid.count", 1, "Valid count for Group12Var1", Some(1), Some(255))
    private val onTime = OptionTypes.integer(group, s"$id.valid.onTime.ms", 100, "On time in ms", Some(0), Some(65535))
    private val offTime =
      OptionTypes.integer(group, s"$id.valid.offTime.ms", 100, "Off time in ms", Some(0), Some(65535))

    val options: List[TestOption] = List(index, function, count, onTime, offTime)

    def getConfig(src: OptionSource): CommandConfig[Group12Var1] = {
      CommandConfig(
        src.get(index),
        Group12Var1(
          ControlCode(src.get(function)),
          src.get(count).toByte,
          UInt32LE(src.get(onTime)),
          UInt32LE(src.get(offTime)),
          CommandStatus.SUCCESS)
      )
    }
  }

  case class ControlCodePointOptions(group: OptionGroup, id: String, defaultIndex: Index, description: String) {
    val index = OptionTypes.optionalIndex(group, s"$id.index", Some(defaultIndex), description, Index.maxOneOctet)
    private val count = OptionTypes.integer(group, s"$id.count", 1, "Valid count for Group12Var1", Some(1), Some(255))
    private val onTime = OptionTypes.integer(group, s"$id.onTime.ms", 100, "On time in ms", Some(0), Some(65535))
    private val offTime = OptionTypes.integer(group, s"$id.offTime.ms", 100, "Off time in ms", Some(0), Some(65535))

    val options: List[TestOption] = List(index, count, onTime, offTime)

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
              src.get(count).toByte,
              UInt32LE(src.get(onTime)),
              UInt32LE(src.get(offTime)),
              CommandStatus.SUCCESS)
          ))
        case None => None
      }
    }
  }

}
