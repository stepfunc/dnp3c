// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol.parsing

case class Bits(byte: Byte) {

  def right(i: Int = 8): List[Boolean] = some(i) {
    bit => 1 << (bit - 1)

  }

  private def some(i: Int)(mask: Int => Int): List[Boolean] = {
    assert(i >= 1)
    assert(i <= 8)
    (1 to i).foldLeft(List.empty[Boolean]) { (list, bit) =>
      val bitmask = mask(bit)
      val set = (bitmask & byte) != 0
      set :: list
    }.reverse
  }
}
