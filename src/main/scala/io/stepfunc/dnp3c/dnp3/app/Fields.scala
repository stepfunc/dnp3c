// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import io.stepfunc.dnp3c.dnp3.app.objects.{ AttributeValue, CommandStatus, ControlCode }
import io.stepfunc.dnp3c.protocol.Hex
import io.stepfunc.dnp3c.protocol.parsing._

object Fields {

  // common labels
  val flags: FieldParser[Byte] = FieldParser.flags
  val flagsWithBinary: FieldParser[Byte] = FieldParser.flagsWithBinary
  val flagsWithDoubleBit: FieldParser[Byte] = FieldParser.flagsWithDoubleBit

  val index16: FieldParser[UInt16LE] = FieldParser.u16("index", false)

  val count8: FieldParser[Byte] = FieldParser.u8x10("count")
  val count16: FieldParser[UInt16LE] = FieldParser.u16("count", false)
  val count32: FieldParser[UInt32LE] = FieldParser.u32("count")

  val value8: FieldParser[Byte] = FieldParser.u8x16("value")
  val value16: FieldParser[Short] = FieldParser.s16("value")
  val value32: FieldParser[Int] = FieldParser.s32("value")
  val valueF32: FieldParser[Float] = FieldParser.f32("value")
  val valueF64: FieldParser[Double] = FieldParser.f64("value")

  val timeCTO: FieldParser[UInt16LE] = FieldParser.u16("cto", true)
  val delay16: FieldParser[UInt16LE] = FieldParser.u16("delay", true)
  val time48: FieldParser[UInt48LE] = FieldParser.u48("time")
  val interval32: FieldParser[UInt32LE] = FieldParser.u32("interval")

  val attribute: FieldParser[(Byte, List[Byte])] =
    FieldParser.attribute((code, value) => AttributeValue.decode(code, value).description)

  // control related stuff
  val code: FieldParser[Byte] = FieldParser.u8("code", x => s"${Hex.prefixed(x)} (${ControlCode(x).name})")
  val onTime: FieldParser[UInt32LE] = FieldParser.u32("onTime")
  val offTime: FieldParser[UInt32LE] = FieldParser.u32("offTime")
  val status: FieldParser[Byte] = FieldParser.u8("status", x => s"${0xFF & x} (${CommandStatus.get(x).name})")

}
