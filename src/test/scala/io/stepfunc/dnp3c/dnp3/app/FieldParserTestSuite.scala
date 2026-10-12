// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import io.stepfunc.dnp3c.dnp3.app.Fields._
import io.stepfunc.dnp3c.protocol.Hex
import io.stepfunc.dnp3c.protocol.parsing.{ Field, Parser, UInt16LE, UInt32LE }
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class FieldParserTestSuite extends AnyFunSuite with Matchers {

  test("Can parse U16") {
    val result = count16.parse(Hex.from("01 02 03"), new OtherPointBuilder)
    result.remainder should equal(List(0x03.toByte))
    result.value.toBytes should equal(List(0x01.toByte, 0x02.toByte))
    result.value.description should equal("count: 513")
  }

  test("Can parse S16") {
    val result = value16.parse(Hex.from("FF 00 03"), new OtherPointBuilder)
    result.remainder should equal(List(0x03.toByte))
    result.value.toBytes should equal(List(0xFF.toByte, 0x00.toByte))
    result.value.description should equal("value: 255")
  }

  test("Can parse U32 with byte values greater than signed") {
    val result = count32.parse(Hex.from("AA BB CC DD EE"), new OtherPointBuilder)
    result.remainder should equal(List(0xEE.toByte))
    result.value.toBytes should equal(List(0xAA.toByte, 0xBB.toByte, 0xCC.toByte, 0xDD.toByte))
    result.value.description should equal("count: 3721182122")
  }

  test("Can parse S32") {
    val result = value32.parse(Hex.from("FF 00 00 00 05"), new OtherPointBuilder)
    result.remainder should equal(List(0x05.toByte))
    result.value.toBytes should equal(List(0xFF.toByte, 0x00.toByte, 0x00.toByte, 0x00.toByte))
    result.value.description should equal("value: 255")
  }

  test("Can parse UInt48") {
    val result = time48.parse(Hex.from("AA BB CC DD EE FF 01"), new OtherPointBuilder)
    result.remainder should equal(List(0x01.toByte))
    result.value.toBytes should
      equal(List(0xAA.toByte, 0xBB.toByte, 0xCC.toByte, 0xDD.toByte, 0xEE.toByte, 0xFF.toByte))
    result.value.description should equal("time: 281401388481450")
  }

  test("Can collect fields") {
    val builder = new OtherPointBuilder

    // create an aggregate parser
    val parser = Parser.sequence(
      new FieldPointParserAdapter(flags, builder),
      new FieldPointParserAdapter(count32, builder),
      new FieldPointParserAdapter(index16, builder))(Field.Collection.apply)
    val result = parser.parse(Hex.from("FF 03 00 00 00 01 02 0A"))

    result.remainder should equal(List(0x0A.toByte))
    result.value.description should equal("flags: 0xFF count: 3 index: 513")

    // build the fields manually
    val expected = Field.collect(
      Field.flags(0xFF.toByte),
      Field.u32("count", UInt32LE(3)),
      Field.u16("index", UInt16LE(513)))

    result.value should equal(expected)
  }

  test("Can parse float values") {
    val result = valueF32.parse(Hex.from("00 00 A0 C1 0A"), new OtherPointBuilder)
    result.remainder should equal(List(0x0A.toByte))
    result.value.toBytes should equal(Hex.from("00 00 A0 C1"))
    result.value.description should equal("value: -20.0")
  }

  test("Can parse double values") {
    val result = valueF64.parse(Hex.from("00 00 00 00 00 00 59 40 0A"), new OtherPointBuilder)
    result.remainder should equal(List(0x0A.toByte))
    result.value.toBytes should equal(Hex.from("00 00 00 00 00 00 59 40"))
    result.value.description should equal("value: 100.0")
  }
}
