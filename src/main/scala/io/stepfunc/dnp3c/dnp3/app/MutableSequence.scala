// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

object MutableSequence {
  val modulo = 16
  def zero = MutableSequence(0)

  implicit def toImmutableSequence(seq: MutableSequence): Sequence = Sequence(seq.value)
}

case class MutableSequence(var value: Byte) {
  def increment(): Sequence = {
    this.value = ((this.value + 1) % MutableSequence.modulo).toByte
    Sequence(value)
  }
}
