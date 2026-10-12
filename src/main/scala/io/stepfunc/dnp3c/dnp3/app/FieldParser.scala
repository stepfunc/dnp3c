// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import io.stepfunc.dnp3c.protocol.parsing._

import java.nio.{ ByteBuffer, ByteOrder }

sealed trait FieldPointParser {
  def parse(input: List[Byte], pointBuilder: PointBuilder): Parser.Result[Field]
}

class FieldPointParserAdapter(parser: FieldPointParser, pointBuilder: PointBuilder) extends Parser[Field] {
  override def parse(input: List[Byte]): Parser.Result[Field] = {
    parser.parse(input, pointBuilder)
  }
}

sealed trait FieldParser[A] extends FieldPointParser {
  def apply(value: A): Field
}

object FieldParser {

  val flags: FieldParser[Byte] = Details.Flags(false)
  val flagsWithBinary: FieldParser[Byte] = Details.Flags(true)
  val flagsWithDoubleBit: FieldParser[Byte] = Details.DoubleBitFlags()

  def u8x10(name: String): FieldParser[Byte] = Details.U8(name, Field.u8x10)
  def u8x16(name: String): FieldParser[Byte] = Details.U8(name, Field.u8x16)
  def u8(name: String, format: Byte => String): FieldParser[Byte] =
    Details.U8(name, (n, value) => Field.Types.U8(n, value, format(value)))

  def u16(name: String, isTime: Boolean): FieldParser[UInt16LE] = Details.U16(name, isTime)
  def s16(name: String): FieldParser[Short] = Details.S16(name)

  def u32(name: String): FieldParser[UInt32LE] = Details.U32(name)
  def s32(name: String): FieldParser[Int] = Details.S32(name)

  def u48(name: String): FieldParser[UInt48LE] = Details.U48(name)

  // a device attribute as its data type code and value octets, described by format
  def attribute(format: (Byte, List[Byte]) => String): FieldParser[(Byte, List[Byte])] = Details.Attribute(format)

  def f32(name: String): FieldParser[Float] = Details.F32(name)
  def f64(name: String): FieldParser[Double] = Details.F64(name)

  private object Details {

    sealed case class Flags(containsBinaryValue: Boolean) extends FieldParser[Byte] {
      def apply(value: Byte): Field = Field.flags(value)
      def parse(input: List[Byte], pointBuilder: PointBuilder): Parser.Result[Field] = input match {
        case b :: remainder => {
          if (containsBinaryValue) {
            pointBuilder.onBooleanValue((b & 0x80) != 0)
          }
          pointBuilder.onFlags(b)
          Parser.Result(apply(b), remainder)
        }
        case _ => throw new Exception("Insufficient bytes for flags field")
      }
    }

    sealed case class DoubleBitFlags() extends FieldParser[Byte] {
      def apply(value: Byte): Field = Field.flags(value)
      def parse(input: List[Byte], pointBuilder: PointBuilder): Parser.Result[Field] = input match {
        case b :: remainder => {
          pointBuilder.onDoubleBitValue(DoubleBit((UInt8.byteToShort(b) >> 6).toByte))
          pointBuilder.onFlags(b)
          Parser.Result(apply(b), remainder)
        }
        case _ => throw new Exception("Insufficient bytes for flags field")
      }
    }

    sealed case class U8(name: String, create: (String, Byte) => Field) extends FieldParser[Byte] {
      def apply(value: Byte): Field = create(name, value)
      def parse(input: List[Byte], pointBuilder: PointBuilder): Parser.Result[Field] = input match {
        case b :: remainder => {
          Parser.Result(create(name, b), remainder)
        }
        case _ => throw new Exception("Insufficient bytes for U8: " + name)
      }
    }

    sealed case class Attribute(format: (Byte, List[Byte]) => String) extends FieldParser[(Byte, List[Byte])] {
      def apply(value: (Byte, List[Byte])): Field = Field.attribute(value._1, value._2, format(value._1, value._2))
      def parse(input: List[Byte], pointBuilder: PointBuilder): Parser.Result[Field] = input match {
        case code :: length :: remainder =>
          val (value, rest) = remainder.splitAt(Field.Types.attributeLength(code, length))
          if (value.length != Field.Types.attributeLength(code, length)) {
            throw new Exception("Insufficient bytes for device attribute value")
          }
          Parser.Result(apply((code, value)), rest)
        case _ => throw new Exception("Insufficient bytes for device attribute header")
      }
    }

    sealed case class U16(name: String, isTime: Boolean) extends FieldParser[UInt16LE] {
      def apply(value: UInt16LE): Field = Field.u16(name, value)
      def parse(input: List[Byte], pointBuilder: PointBuilder): Parser.Result[Field] = input match {
        case b1 :: b2 :: remainder =>
          val field = Field.u16(name, UInt16LE(b1, b2))
          if (isTime) {
            pointBuilder.onTimestamp(Timestamp(field.value.value, true))
          } else {
            pointBuilder.onIntegerValue(field.value.value)
          }
          Parser.Result(field, remainder)
        case _ => throw new Exception("Insufficient bytes for U16: " + name)
      }
    }

    sealed case class S16(name: String) extends FieldParser[Short] {
      def apply(value: Short): Field = Field.s16(name, SInt16LE(value))
      def parse(input: List[Byte], pointBuilder: PointBuilder): Parser.Result[Field] = input match {
        case b1 :: b2 :: remainder =>
          val field = Field.s16(name, SInt16LE(b1, b2))
          pointBuilder.onIntegerValue(field.value.value)
          Parser.Result(field, remainder)
        case _ => throw new Exception("Insufficient bytes for S16: " + name)
      }
    }

    sealed case class U32(name: String) extends FieldParser[UInt32LE] {
      def apply(value: UInt32LE): Field = Field.u32(name, value)
      def parse(input: List[Byte], pointBuilder: PointBuilder): Parser.Result[Field] = input match {
        case b1 :: b2 :: b3 :: b4 :: remainder =>
          val field = Field.u32(name, UInt32LE(b1, b2, b3, b4))
          pointBuilder.onIntegerValue(field.value.value)
          Parser.Result(field, remainder)
        case _ => throw new Exception("Insufficient bytes for U32: " + name)
      }
    }

    sealed case class S32(name: String) extends FieldParser[Int] {
      def apply(value: Int): Field = Field.s32(name, SInt32LE(value))
      def parse(input: List[Byte], pointBuilder: PointBuilder): Parser.Result[Field] = input match {
        case b1 :: b2 :: b3 :: b4 :: remainder =>
          val field = Field.s32(name, SInt32LE(b1, b2, b3, b4))
          pointBuilder.onIntegerValue(field.value.value)
          Parser.Result(field, remainder)
        case _ => throw new Exception("Insufficient bytes for S32: " + name)
      }
    }

    sealed case class U48(name: String) extends FieldParser[UInt48LE] {
      def apply(value: UInt48LE): Field = Field.u48(name, value)
      def parse(input: List[Byte], pointBuilder: PointBuilder): Parser.Result[Field] = input match {
        case b1 :: b2 :: b3 :: b4 :: b5 :: b6 :: remainder =>
          val field = Field.u48(name, UInt48LE(b1, b2, b3, b4, b5, b6))
          pointBuilder.onTimestamp(Timestamp(field.value.value, false))
          Parser.Result(field, remainder)
        case _ => throw new Exception("Insufficient bytes for U48: " + name)
      }
    }

    sealed case class F32(name: String) extends FieldParser[Float] {
      def apply(value: Float): Field = Field.f32(name, value)
      def parse(input: List[Byte], pointBuilder: PointBuilder): Parser.Result[Field] = input match {
        case b1 :: b2 :: b3 :: b4 :: remainder =>
          val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).put(b1).put(b2).put(b3).put(b4)
          buffer.rewind()
          val field = Field.f32(name, buffer.getFloat())
          pointBuilder.onFloatingValue(field.value)
          Parser.Result(field, remainder)
        case _ => throw new Exception("Insufficient bytes for F32: " + name)
      }
    }

    sealed case class F64(name: String) extends FieldParser[Double] {
      def apply(value: Double): Field = Field.f64(name, value)
      def parse(input: List[Byte], pointBuilder: PointBuilder): Parser.Result[Field] = input match {
        case b1 :: b2 :: b3 :: b4 :: b5 :: b6 :: b7 :: b8 :: remainder =>
          val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
          buffer.put(b1).put(b2).put(b3).put(b4).put(b5).put(b6).put(b7).put(b8)
          buffer.rewind()
          val field = Field.f64(name, buffer.getDouble())
          pointBuilder.onFloatingValue(field.value)
          Parser.Result(field, remainder)
        case _ => throw new Exception("Insufficient bytes for F64: " + name)
      }
    }
  }

}
