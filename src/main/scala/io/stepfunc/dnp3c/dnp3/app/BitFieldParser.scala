// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import io.stepfunc.dnp3c.protocol.parsing.{ Bits, Parser }

import scala.annotation.tailrec

object BitFieldParser {

  def apply(count: Int): Parser[List[Boolean]] = new Impl(count)

  private class Impl(count: Int) extends Parser[List[Boolean]] {

    assert(count > 0)

    override def parse(input: List[Byte]): Parser.Result[List[Boolean]] = {
      recurse(count, input, Nil).map(x => x.reverse.flatten)
    }

    @tailrec
    private def recurse(i: Int, input: List[Byte], sum: List[List[Boolean]]): Parser.Result[List[List[Boolean]]] =
      input match {
        case b :: remainder =>
          if (i <= 8) {
            Parser.Result(Bits(b).right(i) :: sum, remainder)
          } else {
            recurse(i - 8, remainder, Bits(b).right() :: sum)
          }
        case Nil => throw new Exception("Insufficient bytes for bit-field")
      }
  }

}

object DoubleBitFieldParser {

  def apply(count: Int): Parser[List[DoubleBit]] = new Impl(count)

  private class Impl(count: Int) extends Parser[List[DoubleBit]] {

    assert(count > 0)

    override def parse(input: List[Byte]): Parser.Result[List[DoubleBit]] = {
      recurse(count, input, Nil).map(x => x.reverse.flatten)
    }

    @tailrec
    private def recurse(i: Int, input: List[Byte], sum: List[List[DoubleBit]]): Parser.Result[List[List[DoubleBit]]] =
      input match {
        case b :: remainder =>
          if (i <= 4) {
            Parser.Result(DoubleBits.get(b).take(i) :: sum, remainder)
          } else {
            recurse(i - 4, remainder, DoubleBits.get(b) :: sum)
          }
        case Nil => throw new Exception("Insufficient bytes for double bit-field")
      }
  }

}

object DoubleBits {

  def get(byte: Byte): List[DoubleBit] = {

    def get(value: Byte): DoubleBit = value & 0x03 match {
      case 0x00 => DoubleBit.Intermediate
      case 0x01 => DoubleBit.DeterminedOff
      case 0x02 => DoubleBit.DeterminedOn
      case 0x03 => DoubleBit.Indeterminate
    }

    List(
      get((byte >> 0).toByte),
      get((byte >> 2).toByte),
      get((byte >> 4).toByte),
      get((byte >> 6).toByte)
    )
  }

}
