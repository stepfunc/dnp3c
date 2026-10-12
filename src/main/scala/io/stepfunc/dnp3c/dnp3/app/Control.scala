// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import io.stepfunc.dnp3c.protocol.Hex
import io.stepfunc.dnp3c.protocol.logging.Digit
import io.stepfunc.dnp3c.protocol.parsing.Mask

object Control {

  object Masks {
    val FIR = Mask(0x80.toByte)
    val FIN = Mask(0x40.toByte)
    val CON = Mask(0x20.toByte)
    val UNS = Mask(0x10.toByte)
    val SEQ = Mask(0x0F.toByte)
  }

  def apply(value: Byte): Control = {

    Control(
      First(Masks.FIR.isSet(value)),
      Final(Masks.FIN.isSet(value)),
      Confirm(Masks.CON.isSet(value)),
      Unsolicited(Masks.UNS.isSet(value)),
      Sequence(Masks.SEQ.apply(value))
    )
  }

  def request(seq: Sequence): Control = Control(First.Yes, Final.Yes, Confirm.No, Unsolicited.No, seq)

  def unsolicited(seq: Sequence): Control = Control(First.Yes, Final.Yes, Confirm.No, Unsolicited.Yes, seq)
}

case class Control(fir: First, fin: Final, con: Confirm, uns: Unsolicited, seq: Sequence) {

  import Control._

  def toByte: Byte = {

    Mask.build(
      Masks.FIR.get(fir.value),
      Masks.FIN.get(fin.value),
      Masks.CON.get(con.value),
      Masks.UNS.get(uns.value),
      Masks.SEQ.apply(seq.value))

  }

  override def toString: String = {
    "fir: " + Digit(fir.value) + " fin: " + Digit(fin.value) + " con: " + Digit(con.value) + " uns: " +
      Digit(uns.value) + " seq: " + Hex.prefixed(seq.value)
  }
}
