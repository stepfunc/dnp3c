// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.protocol.Hex
import io.stepfunc.dnp3c.protocol.parsing.{ Field, Integers }

import java.nio.charset.{ CharacterCodingException, CodingErrorAction, StandardCharsets }
import java.nio.ByteBuffer

// data type code of a device attribute (g0)
case class AttrDataType(value: Byte, name: String)

object AttrDataType {

  val VSTR: AttrDataType = AttrDataType(1.toByte, "VSTR")
  val UINT: AttrDataType = AttrDataType(2.toByte, "UINT")
  val INT: AttrDataType = AttrDataType(3.toByte, "INT")
  val FLT: AttrDataType = AttrDataType(4.toByte, "FLT")
  val OSTR: AttrDataType = AttrDataType(5.toByte, "OSTR")
  val BSTR: AttrDataType = AttrDataType(6.toByte, "BSTR")
  // not in IEEE 1815-2012, but used for the configuration date attributes (198, 199)
  val TIME: AttrDataType = AttrDataType(7.toByte, "TIME")
  val LIST: AttrDataType = AttrDataType(254.toByte, "U8BS8LIST")
  val EXLIST: AttrDataType = AttrDataType(255.toByte, "U8BS8EXLIST")

  val list: List[AttrDataType] = List(VSTR, UINT, INT, FLT, OSTR, BSTR, TIME, LIST, EXLIST)

  def unknown(value: Byte): AttrDataType = AttrDataType(value, "Unknown")

  private val map: Map[Byte, AttrDataType] = list.map(x => (x.value, x)).toMap

  def get(value: Byte): AttrDataType = map.getOrElse(value, unknown(value))

}

// an entry in the list of attribute variations (g0v255): properties 0x00 is read-only, 0x01 read/write
case class AttributeListEntry(variation: Byte, properties: Byte) {
  def description: String = properties match {
    case 0 => s"${0xFF & variation}"
    case 1 => s"${0xFF & variation} (rw)"
    case _ => s"${0xFF & variation} (properties ${Hex.prefixed(properties)})"
  }
}

// a view of a device attribute's value, decoded from its data type code and value octets
sealed trait AttributeValue {
  def description: String
}

object AttributeValue {

  case class VisibleString(value: String) extends AttributeValue {
    def description: String = "vstr: \"" + value + "\""
  }
  case class UnsignedInt(value: Long) extends AttributeValue {
    def description: String = s"uint: $value"
  }
  case class SignedInt(value: Int) extends AttributeValue {
    def description: String = s"int: $value"
  }
  case class FloatingPoint(value: Double) extends AttributeValue {
    def description: String = s"flt: $value"
  }
  case class OctetString(value: List[Byte]) extends AttributeValue {
    def description: String = s"ostr: ${Hex.convert(value)}"
  }
  case class BitString(value: List[Byte]) extends AttributeValue {
    def description: String = s"bstr: ${Hex.convert(value)}"
  }
  // milliseconds since 1970-01-01 UTC
  case class Time(value: Long) extends AttributeValue {
    def description: String = s"time: $value"
  }
  case class VariationList(entries: List[AttributeListEntry]) extends AttributeValue {
    def description: String = s"list: ${entries.map(_.description).mkString(", ")}"
  }
  // a complete value that doesn't decode, e.g. a 3-octet UINT
  case class Invalid(dataType: AttrDataType, reason: String) extends AttributeValue {
    def description: String = s"invalid ${dataType.name}: $reason"
  }
  case class Unknown(code: Byte, value: List[Byte]) extends AttributeValue {
    def description: String = s"unknown data type ${0xFF & code}: ${Hex.convert(value)}"
  }

  def decode(code: Byte, value: List[Byte]): AttributeValue = {
    val dataType = AttrDataType.get(code)
    def invalidLength = Invalid(dataType, s"length ${value.length}")
    dataType match {
      case AttrDataType.VSTR => decodeUtf8(value).map(VisibleString.apply).getOrElse(Invalid(dataType, "not UTF-8"))
      case AttrDataType.UINT => if (isIntegerLength(value)) UnsignedInt(Integers.fromBytesLE(value)) else invalidLength
      case AttrDataType.INT => if (isIntegerLength(value)) SignedInt(signExtend(value)) else invalidLength
      case AttrDataType.FLT => value.length match {
          case 4 => FloatingPoint(java.lang.Float.intBitsToFloat(Integers.fromBytesLE(value).toInt).toDouble)
          case 8 => FloatingPoint(java.lang.Double.longBitsToDouble(Integers.fromBytesLE(value)))
          case _ => invalidLength
        }
      case AttrDataType.OSTR => OctetString(value)
      case AttrDataType.BSTR => BitString(value)
      case AttrDataType.TIME => if (value.length == 6) Time(Integers.fromBytesLE(value)) else invalidLength
      case AttrDataType.LIST | AttrDataType.EXLIST =>
        if (value.length % 2 == 0) VariationList(value.grouped(2).map(x => AttributeListEntry(x(0), x(1))).toList)
        else invalidLength
      case _ => Unknown(code, value)
    }
  }

  // the data type code and value octets of a visible string
  def encode(value: String): (Byte, List[Byte]) =
    (AttrDataType.VSTR.value, value.getBytes(StandardCharsets.UTF_8).toList)

  private def isIntegerLength(value: List[Byte]): Boolean = List(1, 2, 4).contains(value.length)

  private def signExtend(value: List[Byte]): Int = {
    val shift = 32 - 8 * value.length
    (Integers.fromBytesLE(value).toInt << shift) >> shift
  }

  private def decodeUtf8(value: List[Byte]): Option[String] = {
    val decoder = StandardCharsets.UTF_8.newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT)
    try Some(decoder.decode(ByteBuffer.wrap(value.toArray)).toString)
    catch { case _: CharacterCodingException => None }
  }

}

// a device attribute received in a response: its variation, the attribute set (the object index) and the raw field
case class ReceivedAttribute(variation: Int, set: Int, raw: Field.Types.Attribute) {
  def value: AttributeValue = AttributeValue.decode(raw.code, raw.value)
}

object ReceivedAttribute {

  // every header must be group 0 and carry exactly one attribute
  def from(message: Message): List[ReceivedAttribute] = message.headers.map(from)

  def from(header: ObjectHeader): ReceivedAttribute = {
    if (header.variation.group != Group0) throw new Exception(s"Expected only device attributes, but received $header")
    val (set, fields) = header match {
      case OneByteStartStopHeader(_, start, stop, FieldHeaderPayload(List(obj))) if start == stop =>
        (start.value, obj.obj)
      case TwoByteStartStopHeader(_, start, stop, FieldHeaderPayload(List(obj))) if start == stop =>
        (start.value, obj.obj)
      case OneByteCountAndIndexHeader(_, _, PrefixedPayload.U8(List(prefix))) =>
        (prefix.index.value, prefix.instance.obj)
      case TwoByteCountAndIndexHeader(_, _, PrefixedPayload.U16(List(prefix))) =>
        (prefix.index.value, prefix.instance.obj)
      case _ => throw new Exception(s"$header must carry exactly one device attribute")
    }
    if (set > 255) throw new Exception(s"$header: attribute set $set is not in 0 to 255")
    fields.fields match {
      case List(attribute: Field.Types.Attribute) => ReceivedAttribute(0xFF & header.variation.id, set, attribute)
      case _ => throw new Exception(s"$header must carry exactly one device attribute")
    }
  }
}
