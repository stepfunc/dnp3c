// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import io.stepfunc.dnp3c.protocol.parsing.ToBytes

object IIN {

  val empty: IIN = IIN(0.toByte, 0.toByte)

  val BROADCAST = IIN(0x01.toByte, 0)
  val CLASS1_EVENTS = IIN(0x02.toByte, 0)
  val CLASS2_EVENTS = IIN(0x04.toByte, 0)
  val CLASS3_EVENTS = IIN(0x08.toByte, 0)
  val NEED_TIME = IIN(0x10.toByte, 0)
  val LOCAL_CONTROL = IIN(0x20.toByte, 0)
  val DEVICE_TROUBLE = IIN(0x40.toByte, 0)
  val DEVICE_RESTART = IIN(0x80.toByte, 0)

  val FUNC_NOT_SUPPORTED = IIN(0, 0x01.toByte)
  val OBJECT_UNKNOWN = IIN(0, 0x02.toByte)
  val PARAM_ERROR = IIN(0, 0x04.toByte)
  val EVENT_BUFFER_OVERFLOW = IIN(0, 0x08.toByte)
  val ALREADY_EXECUTING = IIN(0, 0x10.toByte)
  val CONFIG_CORRUPT = IIN(0, 0x20.toByte)
  val RESERVED1 = IIN(0, 0x40.toByte)
  val RESERVED2 = IIN(0, 0x80.toByte)

  // values that might be set at restart for devices
  val startupBits: IIN = NEED_TIME | DEVICE_RESTART
  val errors: IIN = FUNC_NOT_SUPPORTED | OBJECT_UNKNOWN | PARAM_ERROR
  val iin2x: IIN = IIN(0, 0xFF.toByte)

  case class Named(mask: IIN, name: String)

  val iin1names = List(
    Named(BROADCAST, "Broadcast"),
    Named(CLASS1_EVENTS, "Class1Events"),
    Named(CLASS2_EVENTS, "Class2Events"),
    Named(CLASS3_EVENTS, "Class3Events"),
    Named(NEED_TIME, "NeedTime"),
    Named(LOCAL_CONTROL, "LocalControl"),
    Named(DEVICE_TROUBLE, "DeviceTrouble"),
    Named(DEVICE_RESTART, "DeviceRestart")
  )

  val iin2names = List(
    Named(FUNC_NOT_SUPPORTED, "FuncNotSupported"),
    Named(OBJECT_UNKNOWN, "ObjectUnknown"),
    Named(PARAM_ERROR, "ParamError"),
    Named(EVENT_BUFFER_OVERFLOW, "EventBufferOverflow"),
    Named(ALREADY_EXECUTING, "AlreadyExecuting"),
    Named(CONFIG_CORRUPT, "ConfigCorrupt"),
    Named(RESERVED1, "Reserved1"),
    Named(RESERVED2, "Reserved2")
  )

}

case class IIN(lsb: Byte, msb: Byte) extends ToBytes {

  def isEmpty: Boolean = (lsb == 0) && (msb == 0)

  def nonEmpty: Boolean = !isEmpty

  def unary_~ = IIN((~lsb).toByte, (~msb).toByte)

  def intersects(other: IIN): Boolean = (this & other).nonEmpty

  def -(rhs: IIN): IIN = this & (~rhs)

  def |(rhs: IIN): IIN = IIN((lsb | rhs.lsb).toByte, (msb | rhs.msb).toByte)

  def &(rhs: IIN): IIN = IIN((lsb & rhs.lsb).toByte, (msb & rhs.msb).toByte)

  import IIN._

  override def toBytes = List(lsb, msb)

  override def toString: String = {
    val bits = labels().mkString(", ")
    s"IIN($bits)"
  }

  def labels(): List[String] = labels1() ::: labels2()

  private def labels1(): List[String] = {
    iin1names.foldLeft(List.empty[String])((sum, iin) => if (iin.mask.intersects(this)) iin.name :: sum else sum)
  }

  private def labels2(): List[String] = {
    iin2names.foldLeft(List.empty[String])((sum, iin) => if (iin.mask.intersects(this)) iin.name :: sum else sum)
  }
}
