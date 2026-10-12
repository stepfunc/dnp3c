// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.link

import io.stepfunc.dnp3c.protocol.parsing._

import scala.annotation.tailrec

// This is a DNP specific CRC algorithm

trait CrcGenerator {

  def calc(arr: Array[Byte]): Short

  def calc(list: List[Byte]): Short = calc(list.toArray)

  def append(arr: List[Byte]): List[Byte] = join(arr, SInt16LE(calc(arr)))

  def interlace(arr: List[Byte]): List[Byte] = arr.grouped(16).foldLeft(List.empty[Byte])((sum, x) => sum ++ append(x))
}

object Crc extends CrcGenerator {

  // pre-compute the lookup table, happens once doesn't need to be efficient
  private val table = {
    (0 to 255).foldLeft[List[Int]](Nil) { (s, i) =>
      (0 to 7).foldLeft(i) { (crc, j) =>
        if ((crc & 0x1) != 0) (crc >> 1) ^ 0xA6BC else crc >> 1
      } :: s
    }.reverse.toArray
  }

  def calc(arr: Array[Byte]): Short = {
    @tailrec
    def calcCrc(crc: Int, pos: Int): Short = {
      if (pos == arr.length) ((~crc) & 0xFFFF).toShort
      else calcCrc(table((crc ^ arr(pos)) & 0xFF) ^ (crc >> 8), pos + 1)
    }
    calcCrc(0, 0)
  }

}

case class InvalidCrc(offset: Short) extends CrcGenerator {
  def calc(arr: Array[Byte]): Short = {
    val valid = Crc.calc(arr)
    val invalid = (Crc.calc(arr) + offset).toShort
    assert(valid != invalid)
    invalid
  }
}
