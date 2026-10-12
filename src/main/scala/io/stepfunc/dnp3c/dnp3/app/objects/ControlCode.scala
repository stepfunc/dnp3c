// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

// operation type, the low 4 bits of a g12v1 control code
case class OpType(value: Byte, name: String)

object OpType {

  val NUL: OpType = OpType(0.toByte, "NUL")
  val PULSE_ON: OpType = OpType(1.toByte, "PULSE_ON")
  val PULSE_OFF: OpType = OpType(2.toByte, "PULSE_OFF")
  val LATCH_ON: OpType = OpType(3.toByte, "LATCH_ON")
  val LATCH_OFF: OpType = OpType(4.toByte, "LATCH_OFF")

  val list: List[OpType] = List(NUL, PULSE_ON, PULSE_OFF, LATCH_ON, LATCH_OFF)

  def unknown(value: Byte): OpType = OpType(value, "Unknown")

  private val map: Map[Byte, OpType] = list.map(x => (x.value, x)).toMap

  def get(value: Byte): OpType = map.getOrElse(value, unknown(value))

}

// trip-close code, the high 2 bits of a g12v1 control code
case class TripCloseCode(value: Byte, name: String)

object TripCloseCode {

  val NUL: TripCloseCode = TripCloseCode(0.toByte, "NUL")
  val CLOSE: TripCloseCode = TripCloseCode(1.toByte, "CLOSE")
  val TRIP: TripCloseCode = TripCloseCode(2.toByte, "TRIP")
  val RESERVED: TripCloseCode = TripCloseCode(3.toByte, "RESERVED")

  val list: List[TripCloseCode] = List(NUL, CLOSE, TRIP, RESERVED)

  def unknown(value: Byte): TripCloseCode = TripCloseCode(value, "Unknown")

  private val map: Map[Byte, TripCloseCode] = list.map(x => (x.value, x)).toMap

  def get(value: Byte): TripCloseCode = map.getOrElse(value, unknown(value))

}

// the control code of a g12v1 object: an operation type, queue and clear bits, and a trip-close code
case class ControlCode(value: Byte) {

  def opType: OpType = OpType.get((value & 0x0F).toByte)
  def queue: Boolean = (value & 0x10) != 0
  def clear: Boolean = (value & 0x20) != 0
  def tripClose: TripCloseCode = TripCloseCode.get(((value >> 6) & 0x03).toByte)

  // e.g. "PULSE_ON, CLOSE", with QUEUE and CLEAR when those bits are set
  def name: String =
    (List(opType.name, tripClose.name) ++ Option.when(queue)("QUEUE") ++ Option.when(clear)("CLEAR")).mkString(", ")

}

object ControlCode {

  val pulse: ControlCode = ControlCode(0x01.toByte)
  val latchOn: ControlCode = ControlCode(0x03.toByte)
  val latchOff: ControlCode = ControlCode(0x04.toByte)
  val pulseClose: ControlCode = ControlCode(0x41.toByte)
  val pulseTrip: ControlCode = ControlCode(0x81.toByte)

  // the codes the test procedures use
  val list: List[ControlCode] = List(pulse, latchOn, latchOff, pulseClose, pulseTrip)

}
