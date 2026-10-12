// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol.parsing

import io.stepfunc.dnp3c.protocol.Hex

import java.nio.{ ByteBuffer, ByteOrder }

/**
 * Represents a field inside an object. Most fields have a fixed size; a device attribute carries its own length.
 */
sealed trait Field extends ToBytes {
  def description: String
}

sealed trait TypedField[A] extends Field {
  val value: A
}

object Field {

  def collect(fields: Field*): Collection = Collection(fields.toList)

  case class Collection(fields: List[Field]) extends Field {
    override def description: String = fields.map(_.description).mkString(" ")
    override def toBytes: List[Byte] = fields.flatMap(f => f.toBytes)
  }

  // bytes
  def flags(value: Byte): TypedField[Byte] = Types.Flags(value)
  def u8x16(name: String, value: Byte): TypedField[Byte] = Types.U8(name, value, Types.hex(value))
  def u8x10(name: String, value: Byte): TypedField[Byte] = Types.U8(name, value, Types.decimal(value))

  // 16-bit
  def s16(name: String, value: SInt16LE): TypedField[SInt16LE] = Types.S16(name, value)
  def u16(name: String, value: UInt16LE): TypedField[UInt16LE] = Types.U16(name, value)

  // 32-bit
  def s32(name: String, value: SInt32LE): TypedField[SInt32LE] = Types.S32(name, value)
  def u32(name: String, value: UInt32LE): TypedField[UInt32LE] = Types.U32(name, value)

  // 48-bit
  def u48(name: String, value: UInt48LE): TypedField[UInt48LE] = Types.U48(name, value)

  // device attribute (g0): a data type code, a length octet and the value
  def attribute(code: Byte, value: List[Byte], stringValue: String): TypedField[List[Byte]] =
    Types.Attribute(code, value, stringValue)

  // Floating point
  def f32(name: String, value: Float): TypedField[Float] = Types.F32(name, value)
  def f64(name: String, value: Double): TypedField[Double] = Types.F64(name, value)

  object Types {

    def hex(value: Byte): String = Hex.prefixed(value)
    def decimal(value: Byte): String = (0xFF & value.toInt).toString

    abstract sealed class ByteField(name: String, value: Byte, stringValue: String) extends TypedField[Byte] {
      override def toBytes: List[Byte] = List(value)
      override def description: String = name + ": " + stringValue
      override def toString: String = stringValue
    }

    sealed case class Flags(value: Byte) extends ByteField("flags", value, Types.hex(value))

    sealed case class U8(name: String, value: Byte, stringValue: String) extends ByteField(name, value, stringValue)

    sealed case class S16(name: String, value: SInt16LE) extends TypedField[SInt16LE] {
      override def toBytes: List[Byte] = value.toBytes
      override def description: String = name + ": " + value.value
    }

    sealed case class U16(name: String, value: UInt16LE) extends TypedField[UInt16LE] {
      override def toBytes: List[Byte] = value.toBytes
      override def description: String = name + ": " + value.value
    }

    sealed case class S32(name: String, value: SInt32LE) extends TypedField[SInt32LE] {
      override def toBytes: List[Byte] = value.toBytes
      override def description: String = name + ": " + value.value
    }

    sealed case class U32(name: String, value: UInt32LE) extends TypedField[UInt32LE] {
      override def toBytes: List[Byte] = value.toBytes
      override def description: String = name + ": " + value.value
    }

    sealed case class U48(name: String, value: UInt48LE) extends TypedField[UInt48LE] {
      override def toBytes: List[Byte] = value.toBytes
      override def description: String = name + ": " + value.value
    }

    // the extended list data type (255) encodes a length of 256 plus the length octet
    val extendedListCode: Byte = 0xFF.toByte
    def attributeLength(code: Byte, lengthOctet: Byte): Int =
      (if (code == extendedListCode) 256 else 0) + (0xFF & lengthOctet)

    sealed case class Attribute(code: Byte, value: List[Byte], stringValue: String) extends TypedField[List[Byte]] {
      private val lengthOctet = value.length - (if (code == extendedListCode) 256 else 0)
      if (lengthOctet < 0 || lengthOctet > 255) {
        throw new Exception(s"A device attribute with data type ${0xFF & code} can't have ${value.length} octets")
      }
      override def toBytes: List[Byte] = code :: lengthOctet.toByte :: value
      override def description: String = stringValue
    }

    sealed case class F32(name: String, value: Float) extends TypedField[Float] {
      override def toBytes: List[Byte] =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array().toList
      override def description: String = f"$name: $value"
    }

    sealed case class F64(name: String, value: Double) extends TypedField[Double] {
      override def toBytes: List[Byte] =
        ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(value).array().toList
      override def description: String = f"$name: $value"
    }

  }

}
