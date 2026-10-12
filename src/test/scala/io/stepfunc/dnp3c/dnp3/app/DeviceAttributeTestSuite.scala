// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import io.stepfunc.dnp3c.dnp3.app.objects._
import io.stepfunc.dnp3c.protocol.Hex
import io.stepfunc.dnp3c.protocol.parsing.{ Field, UInt8 }
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class DeviceAttributeTestSuite extends AnyFunSuite with Matchers {

  private val response = "C0 81 00 00 "

  // the decoded value of every attribute in the headers, in order
  private def values(headers: List[ObjectHeader]): List[AttributeValue] = headers.flatMap {
    case h: OneByteStartStopHeader => h.payload.asInstanceOf[FieldHeaderPayload].getFieldCollection
    case h: OneByteCountAndIndexHeader => h.payload.asInstanceOf[PrefixedPayload.U8].getFieldCollection
    case h => fail(s"unexpected header: $h")
  }.flatMap(_.fields).map {
    case Field.Types.Attribute(code, value, _) => AttributeValue.decode(code, value)
    case f => fail(s"unexpected field: $f")
  }

  private def parse(hex: String): Message = {
    val message = Message.parse(Hex.from(response + hex))
    Hex.convert(message.toBytes) should equal(Hex.convert(Hex.from(response + hex))) // round trips
    message
  }

  private def decode(hex: String): AttributeValue = values(parse("00 F1 00 00 00 " + hex).headers).head

  test("Reads a single attribute with qualifier 0x00 (IEEE 1815 EX 5-10)") {
    val message = parse("00 F1 00 00 00 02 02 DC 05")
    message.headers.head.variation should equal(Group0(241))
    values(message.headers) should equal(List(AttributeValue.UnsignedInt(1500)))
    message.headers.head.toString should equal("g0v241, 1-Byte Start/Stop, start: 0 stop: 0")
    message.headers.head.contents.toList should equal(List("[0] - uint: 1500"))
  }

  // IEEE 1815 Table 14-5 allows attribute responses, including g0v255, to use qualifier 0x00 or 0x17
  test("Reads a list of attribute variations with qualifier 0x17, keeping the raw properties") {
    val message = parse("00 FF 17 01 00 FE 06 F0 01 F1 00 F2 03")
    values(message.headers) should equal(List(AttributeValue.VariationList(List(
      AttributeListEntry(0xF0.toByte, 1),
      AttributeListEntry(0xF1.toByte, 0),
      AttributeListEntry(0xF2.toByte, 3)))))
    values(message.headers).head.description should equal("list: 240 (rw), 241, 242 (properties 0x03)")
  }

  test("Consumes exactly each attribute's bytes when headers follow each other") {
    val message = parse("00 F7 00 00 00 01 03 61 62 63 00 F8 00 00 00 01 00 01 02 00 00 00 01")
    values(message.headers.take(2)) should
      equal(List(AttributeValue.VisibleString("abc"), AttributeValue.VisibleString("")))
    message.headers(2).variation should equal(Group1Var2)
  }

  test("Reads an extended list, whose length is 256 plus the length octet") {
    val entries = (0 until 130).flatMap(i => List(i.toByte, 0.toByte)).toList
    val message = parse("00 FF 00 00 00 FF 04 " + Hex.convert(entries))
    values(message.headers) should
      equal(List(AttributeValue.VariationList(entries.grouped(2).map(x => AttributeListEntry(x(0), x(1))).toList)))
  }

  test("Throws when an attribute is truncated") {
    an[Exception] should be thrownBy Message.parse(Hex.from(response + "00 F1 00 00 00 02 02 DC"))
    an[Exception] should be thrownBy Message.parse(Hex.from(response + "00 F1 00 00 00 02"))
  }

  test("Decodes complete but malformed values as invalid or unknown") {
    decode("02 03 01 02 03") should equal(AttributeValue.Invalid(AttrDataType.UINT, "length 3"))
    decode("FE 03 F0 01 F1") should equal(AttributeValue.Invalid(AttrDataType.LIST, "length 3"))
    decode("01 02 C3 28") should equal(AttributeValue.Invalid(AttrDataType.VSTR, "not UTF-8"))
    decode("09 01 AA") should equal(AttributeValue.Unknown(9, List(0xAA.toByte)))
  }

  test("Decodes numeric boundaries") {
    decode("02 04 FF FF FF FF") should equal(AttributeValue.UnsignedInt(4294967295L))
    decode("03 01 FF") should equal(AttributeValue.SignedInt(-1))
    decode("03 02 00 80") should equal(AttributeValue.SignedInt(-32768))
    decode("03 04 FF FF FF 7F") should equal(AttributeValue.SignedInt(Int.MaxValue))
    decode("04 04 00 00 C0 3F") should equal(AttributeValue.FloatingPoint(1.5))
    decode("04 08 00 00 00 00 00 00 F8 3F") should equal(AttributeValue.FloatingPoint(1.5))
    decode("07 06 01 00 00 00 00 00") should equal(AttributeValue.Time(1))
  }

  test("A constructed visible string equals the same attribute parsed") {
    val header = OneByteStartStopHeader(
      Group0(245),
      UInt8(0),
      UInt8(0),
      FieldHeaderPayload(FieldHeaderPayloadObject(Group0.visibleString("dnp3c test"))))
    val message = Message(Control.request(Sequence.zero), AppFunction.response, Some(IIN.empty), List(header))
    Message.parse(message.toBytes) should equal(message)
    values(message.headers) should equal(List(AttributeValue.VisibleString("dnp3c test")))
  }

  test("Limits a constructed visible string by its encoded octets, not its characters") {
    Group0.visibleString("\u00e9" * 127).toBytes.length should equal(2 + 254) // 2 octets per character
    an[Exception] should be thrownBy Group0.visibleString("\u00e9" * 128)
    an[Exception] should be thrownBy Group0.visibleString("x" * 256)
  }

  test("Reads several attributes under one prefixed header, keeping their set indexes") {
    val message = parse("00 F7 17 02 01 01 01 61 05 01 03 66 6F 6F")
    val payload = message.headers.head.asInstanceOf[OneByteCountAndIndexHeader].payload.asInstanceOf[PrefixedPayload.U8]
    payload.objects.map(_.index.value) should equal(List(1, 5))
    values(message.headers) should equal(List(AttributeValue.VisibleString("a"), AttributeValue.VisibleString("foo")))
  }

  test("Names group 0 variations with unsigned numbers") {
    Group0(255).toString should equal("g0v255")
  }

}
