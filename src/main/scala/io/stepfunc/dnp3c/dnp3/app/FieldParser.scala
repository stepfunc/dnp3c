// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import io.stepfunc.dnp3c.protocol.parsing._

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

  def u16(name: String, isTime: Boolean): FieldParser[UInt16LE] =
    Details.Fixed("U16", name, 2, v => UInt16LE(v.toInt), Field.u16(name, _)) { (builder, value) =>
      if (isTime) builder.onTimestamp(Timestamp(value.value, true)) else builder.onIntegerValue(value.value)
    }
  def s16(name: String): FieldParser[Short] =
    Details.Fixed("S16", name, 2, _.toShort, v => Field.s16(name, SInt16LE(v)))(_.onIntegerValue(_))

  def u32(name: String): FieldParser[UInt32LE] =
    Details.Fixed("U32", name, 4, UInt32LE(_), Field.u32(name, _))((builder, v) => builder.onIntegerValue(v.value))
  def s32(name: String): FieldParser[Int] =
    Details.Fixed("S32", name, 4, _.toInt, v => Field.s32(name, SInt32LE(v)))(_.onIntegerValue(_))

  def u48(name: String): FieldParser[UInt48LE] =
    Details.Fixed("U48", name, 6, UInt48LE(_), Field.u48(name, _)) { (builder, value) =>
      builder.onTimestamp(Timestamp(value.value, false))
    }

  // a device attribute as its data type code and value octets, described by format
  def attribute(format: (Byte, List[Byte]) => String): FieldParser[(Byte, List[Byte])] = Details.Attribute(format)

  def f32(name: String): FieldParser[Float] =
    Details.Fixed("F32", name, 4, v => java.lang.Float.intBitsToFloat(v.toInt), Field.f32(name, _))(
      _.onFloatingValue(_))
  def f64(name: String): FieldParser[Double] =
    Details.Fixed("F64", name, 8, java.lang.Double.longBitsToDouble, Field.f64(name, _))(_.onFloatingValue(_))

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

    // a field of a fixed number of bytes, least significant first, decoded to a value the point builder is given
    final class Fixed[A](
        kind: String,
        name: String,
        width: Int,
        decode: Long => A,
        field: A => Field)(onValue: (PointBuilder, A) => Unit) extends FieldParser[A] {
      def apply(value: A): Field = field(value)
      def parse(input: List[Byte], pointBuilder: PointBuilder): Parser.Result[Field] = {
        val (bytes, remainder) = input.splitAt(width)
        if (bytes.size < width) throw new Exception(s"Insufficient bytes for $kind: $name")
        val value = decode(Integers.fromBytesLE(bytes))
        onValue(pointBuilder, value)
        Parser.Result(field(value), remainder)
      }
    }
  }

}
