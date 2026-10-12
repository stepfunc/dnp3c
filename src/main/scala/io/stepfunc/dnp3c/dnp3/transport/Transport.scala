// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.transport

import io.stepfunc.dnp3c.protocol.logging.Digit

object Transport {

  // the most application data one link frame carries: 250 octets of user data, less the 1-octet transport header
  val maxPayload = 249

  def next(seq: Int): Byte = rotate(seq + 1)

  def rotate(seq: Int): Byte = (seq % 64).toByte

  val finMask: Byte = 0x80.toByte
  val firMask: Byte = 0x40.toByte
  val seqMask: Byte = 0x3F.toByte

  case class Header(fir: Boolean, fin: Boolean, seq: Byte) {
    override def toString: String = "fir: " + Digit(fir) + " fin: " + Digit(fin) + " seq: " + seq.toInt

    def toByte: Byte = function(fir, fin, seq)
  }

  def function(byte: Byte): Header = {
    val fir = (byte & firMask) != 0
    val fin = (byte & finMask) != 0
    val seq = (byte & seqMask).toByte
    Header(fir, fin, seq)
  }

  def function(fir: Boolean, fin: Boolean, seq: Byte): Byte = {
    val firData = if (fir) firMask else 0
    val finData = if (fin) finMask else 0
    val seqData = seq & seqMask
    (firData | finData | seqData).toByte
  }

}
