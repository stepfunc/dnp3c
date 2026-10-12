// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

// control status (CTLS) of a command object, e.g. g12v1 or g41
case class CommandStatus(value: Byte, name: String)

object CommandStatus {

  val SUCCESS: CommandStatus = CommandStatus(0.toByte, "SUCCESS")
  val TIMEOUT: CommandStatus = CommandStatus(1.toByte, "TIMEOUT")
  val NO_SELECT: CommandStatus = CommandStatus(2.toByte, "NO_SELECT")
  val FORMAT_ERROR: CommandStatus = CommandStatus(3.toByte, "FORMAT_ERROR")
  val NOT_SUPPORTED: CommandStatus = CommandStatus(4.toByte, "NOT_SUPPORTED")
  val ALREADY_ACTIVE: CommandStatus = CommandStatus(5.toByte, "ALREADY_ACTIVE")
  val HARDWARE_ERROR: CommandStatus = CommandStatus(6.toByte, "HARDWARE_ERROR")
  val LOCAL: CommandStatus = CommandStatus(7.toByte, "LOCAL")
  val TOO_MANY_OPS: CommandStatus = CommandStatus(8.toByte, "TOO_MANY_OPS")
  val NOT_AUTHORIZED: CommandStatus = CommandStatus(9.toByte, "NOT_AUTHORIZED")
  val AUTOMATION_INHIBIT: CommandStatus = CommandStatus(10.toByte, "AUTOMATION_INHIBIT")
  val PROCESSING_LIMITED: CommandStatus = CommandStatus(11.toByte, "PROCESSING_LIMITED")
  val OUT_OF_RANGE: CommandStatus = CommandStatus(12.toByte, "OUT_OF_RANGE")
  val NON_PARTICIPATING: CommandStatus = CommandStatus(126.toByte, "NON_PARTICIPATING")
  val UNDEFINED: CommandStatus = CommandStatus(127.toByte, "UNDEFINED")

  val list: List[CommandStatus] = List(
    SUCCESS,
    TIMEOUT,
    NO_SELECT,
    FORMAT_ERROR,
    NOT_SUPPORTED,
    ALREADY_ACTIVE,
    HARDWARE_ERROR,
    LOCAL,
    TOO_MANY_OPS,
    NOT_AUTHORIZED,
    AUTOMATION_INHIBIT,
    PROCESSING_LIMITED,
    OUT_OF_RANGE,
    NON_PARTICIPATING,
    UNDEFINED
  )

  def unknown(value: Byte): CommandStatus = CommandStatus(value, "Unknown")

  private val map: Map[Byte, CommandStatus] = list.map(x => (x.value, x)).toMap

  def get(value: Byte): CommandStatus = map.getOrElse(value, unknown(value))

}
