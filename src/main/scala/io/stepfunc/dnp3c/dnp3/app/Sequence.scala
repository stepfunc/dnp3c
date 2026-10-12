// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import io.stepfunc.dnp3c.protocol.Hex

case class Sequence(value: Byte) {
  def next: Sequence = Sequence(((value + 1) % 16).toByte)

  override def toString: String = "sequence(" + Hex.prefixed(value) + ")"
}

object Sequence {
  val zero: Sequence = Sequence(0)
  val one: Sequence = Sequence(1)
  val two: Sequence = Sequence(2)
  val three: Sequence = Sequence(3)
  val four: Sequence = Sequence(4)
  val five: Sequence = Sequence(5)
}
