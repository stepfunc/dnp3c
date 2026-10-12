// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

object BitFieldBuilder {

  def build(values: List[Boolean]): List[Byte] = {

    def single(values: List[Boolean]): Byte = {
      def bit(value: Boolean, shift: Int): Int = if (value) 1 << shift else 0
      values.zipWithIndex.foldLeft(0)((sum, indexed) => sum | bit(indexed._1, indexed._2)).toByte
    }

    values.grouped(8).map(x => single(x)).toList
  }

  def buildDouble(bits: List[DoubleBit]): List[Byte] = {

    def single(bits: List[DoubleBit]): Byte = {
      def bit(bit: DoubleBit, shift: Int): Int = bit.value << shift
      bits.zipWithIndex.foldLeft(0)((sum, indexed) => sum | bit(indexed._1, 2 * indexed._2)).toByte
    }

    bits.grouped(4).map(single).toList
  }

}
