// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import io.stepfunc.dnp3c.protocol.parsing.Parser

// a bit field's values, of one bit each or two, packed into bytes from the least significant bits up
object BitFields {

  def single(count: Int): Parser[List[Boolean]] = parser(count, 1, "bit-field")(_ != 0)
  def double(count: Int): Parser[List[DoubleBit]] = parser(count, 2, "double bit-field")(DoubleBit.of)

  def pack(values: List[Boolean]): List[Byte] = pack(values.map(v => if (v) 1 else 0), 1)
  def packDouble(values: List[DoubleBit]): List[Byte] = pack(values.map(_.value.toInt), 2)

  private def parser[A](count: Int, width: Int, name: String)(value: Int => A): Parser[List[A]] = {
    assert(count > 0)
    val perByte = 8 / width
    input => {
      val (bytes, remainder) = input.splitAt((count + perByte - 1) / perByte)
      if (bytes.size * perByte < count) throw new Exception(s"Insufficient bytes for $name")
      val values = bytes.flatMap(b => (0 until perByte).map(i => value((b >> (i * width)) & ((1 << width) - 1))))
      Parser.Result(values.take(count), remainder)
    }
  }

  private def pack(values: List[Int], width: Int): List[Byte] =
    values.grouped(8 / width).map(_.zipWithIndex.foldLeft(0)((sum, v) => sum | (v._1 << (v._2 * width))).toByte).toList
}
