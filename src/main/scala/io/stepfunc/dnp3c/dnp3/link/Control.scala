// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.link

import io.stepfunc.dnp3c.protocol.Hex
import io.stepfunc.dnp3c.protocol.logging.Digit
import io.stepfunc.dnp3c.protocol.parsing.{ Mask, ToBytes }

object Control {

  object Masks {
    val MASTER = Mask(0x80.toByte)
    val PRI = Mask(0x40.toByte)
    val FCB = Mask(0x20.toByte)
    val FCV = Mask(0x10.toByte)
    val FUNC = Mask(0x0F.toByte)
  }

  def apply(byte: Byte): Control = {
    val master = Masks.MASTER.isSet(byte)
    val pri = Masks.PRI.isSet(byte)
    val fcb = Masks.FCB.isSet(byte)
    val fcv = Masks.FCV.isSet(byte)
    val func = Masks.FUNC.apply(byte)

    Control(master, fcb, fcv, LinkFunction.get(pri, func))
  }

  def build(master: Boolean, pri: Boolean, fcb: Boolean, fcvdfc: Boolean, func: Byte): Byte = {
    Mask.build(
      Masks.MASTER.get(master),
      Masks.PRI.get(pri),
      Masks.FCB.get(fcb),
      Masks.FCV.get(fcvdfc),
      Masks.FUNC.apply(func))
  }

  def confirmedUserData(fcb: Boolean): Control = Control(true, fcb, true, LinkFunction.CONFIRMED_USER_DATA)

  // an ACK from the master (the harness)
  val ack: Control = Control(true, false, false, LinkFunction.ACK)
}

case class Control(master: Boolean, fcb: Boolean, fcv: Boolean, func: LinkFunction) extends ToBytes {

  assert(func.id.value <= 0xF)

  def toByte: Byte = Control.build(master, func.id.pri, fcb, fcv, func.id.value)

  override def toBytes = List(toByte)

  override def toString: String = {
    "master: " + Digit(master) + " pri: " + Digit(func.id.pri) + " fcb: " + Digit(fcb) + " fcv: " + Digit(fcv) +
      " func: " + func.description + "(" + Hex.prefixed(func.id.value) + ") " + Hex.prefixed(toByte)
  }
}
