// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import io.stepfunc.dnp3c.protocol.parsing.{ Field, Parser, ToBytes, UInt16LE, UInt8 }

/**
 * A payload is something carried by an object header
 */
sealed trait HeaderPayload extends ToBytes {

  // A list of descriptions of the elements in the payload
  def descriptions: Iterable[String]

}

sealed trait IterableHeaderPayload extends HeaderPayload {
  def iterate: Seq[Point]
}

sealed trait HasFieldCollection {
  def getFieldCollection: List[Field.Collection]
}

object FieldHeaderPayloadObject {
  def apply(obj: Field.Collection): FieldHeaderPayloadObject = {
    new FieldHeaderPayloadObject(obj, Other)
  }
}
final case class FieldHeaderPayloadObject(obj: Field.Collection, point: Point)

object FieldHeaderPayload {
  val empty = FieldHeaderPayload(Nil)
  def apply(objects: FieldHeaderPayloadObject*): FieldHeaderPayload = {
    new FieldHeaderPayload(objects.toList)
  }
}

// Used directly with start/stop and count headers
final case class FieldHeaderPayload(objects: List[FieldHeaderPayloadObject]) extends IterableHeaderPayload
    with HasFieldCollection {
  override def toBytes: List[Byte] = objects.flatMap(e => e.obj.toBytes)
  override def descriptions: Iterable[String] = objects.map(e => e.obj.description)
  override def getFieldCollection: List[Field.Collection] = objects.map(e => e.obj)
  override def iterate: Seq[Point] = objects.map(e => e.point)
}

object BooleanValue {
  implicit def convert(value: Boolean): BooleanValue = new BooleanValue(value)
}
case class BooleanValue(value: Boolean)

final case class BitFieldHeaderPayload(bits: List[BooleanValue], isOutputStatus: Boolean)
    extends IterableHeaderPayload {
  override def toBytes: List[Byte] = BitFieldBuilder.build(bits.map(_.value))
  override def descriptions: Iterable[String] = bits.map(x => x.toString)
  override def iterate: Seq[Point] = if (isOutputStatus)
    bits.map(x => BinaryOutputStatusPoint(x.value, None, None, PointType.Static))
  else bits.map(x => BinaryPoint(x.value, None, None, PointType.Static))
}

sealed case class DoubleBit(value: Byte)
object DoubleBit {
  object Intermediate extends DoubleBit(0)
  object DeterminedOff extends DoubleBit(1)
  object DeterminedOn extends DoubleBit(2)
  object Indeterminate extends DoubleBit(3)
}

final case class DoubleBitFieldHeaderPayload(bits: List[DoubleBit]) extends IterableHeaderPayload {
  override def toBytes: List[Byte] = BitFieldBuilder.buildDouble(bits)
  override def descriptions: Iterable[String] = bits.map(x => x.toString)
  override def iterate: Seq[Point] = bits.map(x => DoubleBitPoint(x, None, None, PointType.Static))
}

object Prefix {

  def apply(index: UInt8, fields: Field.Collection): U8 = U8(index, FieldHeaderPayloadObject(fields))

  def apply(index: UInt16LE, fields: Field.Collection): U16 = U16(index, FieldHeaderPayloadObject(fields))

  object U8 {
    def apply(fields: U8Fields, point: Point): U8 = {
      U8(fields.index, FieldHeaderPayloadObject(fields.fields, point))
    }
  }
  case class U8(index: UInt8, instance: FieldHeaderPayloadObject) {
    def bytes: List[Byte] = index.toBytes ::: instance.obj.toBytes
    def description: String = "[" + index.value + "]" + " - " + instance.obj.description
  }

  object U16 {
    def apply(fields: U16Fields, point: Point): U16 = {
      U16(fields.index, FieldHeaderPayloadObject(fields.fields, point))
    }
  }
  case class U16(index: UInt16LE, instance: FieldHeaderPayloadObject) {
    def bytes: List[Byte] = index.toBytes ::: instance.obj.toBytes
    def description: String = "[" + index.value + "]" + " - " + instance.obj.description
  }

  case class U8Fields(index: UInt8, fields: Field.Collection)
  case class U16Fields(index: UInt16LE, fields: Field.Collection)

  def parserU8(parser: Parser[Field.Collection]): Parser[U8Fields] = new Parser[U8Fields] {
    override def parse(input: List[Byte]): Parser.Result[U8Fields] = input match {
      case index :: remainder =>
        val result = parser.parse(remainder)
        Parser.Result(U8Fields(UInt8.fromByte(index), result.value), result.remainder)
      case _ =>
        throw new Exception("Insufficient data for 1-byte prefix")
    }
  }

  def parserU16(parser: Parser[Field.Collection]): Parser[U16Fields] = new Parser[U16Fields] {
    override def parse(input: List[Byte]): Parser.Result[U16Fields] = input match {
      case lsb :: msb :: remainder =>
        val result = parser.parse(remainder)
        Parser.Result(U16Fields(UInt16LE(lsb, msb), result.value), result.remainder)
      case _ =>
        throw new Exception("Insufficient data for 2-byte prefix")
    }
  }
}

sealed trait PrefixedPayload extends HeaderPayload {
  def iterate: Seq[IndexedPayloadItem]
}

object PrefixedPayload {

  case class U8(objects: List[Prefix.U8]) extends PrefixedPayload with HasFieldCollection {
    override def toBytes: List[Byte] = objects.flatMap(_.bytes)
    override def descriptions: Iterable[String] = objects.map(_.description)
    override def getFieldCollection: List[Field.Collection] = objects.map(_.instance.obj)
    override def iterate: Seq[IndexedPayloadItem] =
      objects.map(value => IndexedPayloadItem(value.index.value, value.instance.point))
  }

  case class U16(objects: List[Prefix.U16]) extends PrefixedPayload with HasFieldCollection {
    override def toBytes: List[Byte] = objects.flatMap(_.bytes)
    override def descriptions: Iterable[String] = objects.map(_.description)
    override def getFieldCollection: List[Field.Collection] = objects.map(_.instance.obj)
    override def iterate: Seq[IndexedPayloadItem] =
      objects.map(value => IndexedPayloadItem(value.index.value, value.instance.point))
  }
}
