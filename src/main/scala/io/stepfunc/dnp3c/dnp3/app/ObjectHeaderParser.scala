// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import io.stepfunc.dnp3c.protocol.Hex
import io.stepfunc.dnp3c.protocol.parsing.{ Parser, UInt16LE, UInt8 }

trait ObjectHeaderParser {

  def qualifier: Qualifier

  def parse(variation: Variation, bytes: List[Byte]): Parser.Result[ObjectHeader]
}

object ObjectHeaderParser {

  private val parsers = List(
    AllObjectsParser,
    OneByteStartStopParser,
    TwoByteStartStopParser,
    OneByteCountParser,
    TwoByteCountParser,
    OneByteCountOneByteIndexParser,
    TwoByteCountTwoByteIndexParser
  )

  private val map = parsers.foldLeft(Map.empty[Byte, ObjectHeaderParser]) { (map, p) =>
    if (map.get(p.qualifier.value).isDefined) throw new Exception("Parser already defined")
    else map + (p.qualifier.value -> p)
  }

  def get(qualifier: Byte): ObjectHeaderParser = map.getOrElse(
    qualifier,
    throw new Exception("Unknown qualifier: " + Hex.prefixed(qualifier)))
}

object AllObjectsParser extends ObjectHeaderParser {

  def qualifier: Qualifier = Qualifier.ALL_OBJECTS

  def parse(variation: Variation, bytes: List[Byte]): Parser.Result[ObjectHeader] = {
    Parser.Result(AllObjectsHeader(variation), bytes)
  }

}

object OneByteStartStopParser extends ObjectHeaderParser {

  def qualifier: Qualifier = Qualifier.ONE_BYTE_START_STOP

  def parse(variation: Variation, bytes: List[Byte]): Parser.Result[ObjectHeader] = bytes match {
    case b1 :: b2 :: remainder =>
      val start = UInt8.fromByte(b1)
      val stop = UInt8.fromByte(b2)
      if (stop.value < start.value) throw new Exception("stop < start")
      val result = variation.parse(start.value to stop.value, remainder)
      val header = OneByteStartStopHeader(variation, start, stop, result.value)
      Parser.Result(header, result.remainder)
    case _ =>
      throw new Exception("Not enough data for: " + qualifier.name)
  }
}

object TwoByteStartStopParser extends ObjectHeaderParser {

  def qualifier: Qualifier = Qualifier.TWO_BYTE_START_STOP

  def parse(variation: Variation, bytes: List[Byte]): Parser.Result[ObjectHeader] = bytes match {
    case start1 :: start2 :: stop1 :: stop2 :: remainder =>
      val start = UInt16LE(start1, start2)
      val stop = UInt16LE(stop1, stop2)
      if (stop.value < start.value) throw new Exception("stop < start")
      val result = variation.parse(start.value to stop.value, remainder)
      val header = TwoByteStartStopHeader(variation, start, stop, result.value)
      Parser.Result(header, result.remainder)
    case _ =>
      throw new Exception("Not enough data for: " + qualifier.name)
  }
}

object OneByteCountParser extends ObjectHeaderParser {

  def qualifier: Qualifier = Qualifier.ONE_BYTE_COUNT

  def parse(variation: Variation, bytes: List[Byte]): Parser.Result[ObjectHeader] = bytes match {
    case cnt :: remainder =>
      val count = UInt8.fromByte(cnt)
      val result = variation.parse(count.value, remainder)
      val header = OneByteCountHeader(variation, count, result.value)
      Parser.Result(header, result.remainder)
    case _ =>
      throw new Exception("Not enough data for: " + qualifier.name)
  }
}

object TwoByteCountParser extends ObjectHeaderParser {

  def qualifier: Qualifier = Qualifier.TWO_BYTE_COUNT

  def parse(variation: Variation, bytes: List[Byte]): Parser.Result[ObjectHeader] = bytes match {
    case cnt1 :: cnt2 :: remainder =>
      val count = UInt16LE(cnt1, cnt2)
      val result = variation.parse(count.value, remainder)
      val header = TwoByteCountHeader(variation, count, result.value)
      Parser.Result(header, result.remainder)
    case _ =>
      throw new Exception("Not enough data for: " + qualifier.name)
  }
}

object OneByteCountOneByteIndexParser extends ObjectHeaderParser {

  def qualifier: Qualifier = Qualifier.ONE_BYTE_COUNT_AND_INDEX

  def parse(variation: Variation, bytes: List[Byte]): Parser.Result[ObjectHeader] = bytes match {
    case cnt :: remainder =>
      val count = UInt8.fromByte(cnt)
      val result = variation.parsePrefixedU8(count.value, remainder)
      val header = OneByteCountAndIndexHeader(variation, count, result.value)
      Parser.Result(header, result.remainder)
    case _ =>
      throw new Exception("Not enough data for: " + qualifier.name)
  }
}

object TwoByteCountTwoByteIndexParser extends ObjectHeaderParser {

  def qualifier: Qualifier = Qualifier.TWO_BYTE_COUNT_AND_INDEX

  def parse(variation: Variation, bytes: List[Byte]): Parser.Result[ObjectHeader] = bytes match {
    case lsb :: msb :: remainder =>
      val count = UInt16LE(lsb, msb)
      val result = variation.parsePrefixedU16(count.value, remainder)
      val header = TwoByteCountAndIndexHeader(variation, count, result.value)
      Parser.Result(header, result.remainder)
    case _ =>
      throw new Exception("Not enough data for: " + qualifier.name)
  }
}
