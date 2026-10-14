// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol.parsing

object Integers {

  def toBytesBE(value: Long, bytes: Int): List[Byte] = {
    assert(bytes > 0)
    Range(0, bytes, 1).foldLeft(Nil: List[Byte]) { (sum, i) =>
      ((value >> i * 8) & 0xFF).toByte :: sum
    }
  }

  def toBytesLE(value: Long, bytes: Int): List[Byte] = toBytesBE(value, bytes).reverse

  // the bits of up to 8 bytes, least significant first, as a Long: unsigned for fewer than 8
  def fromBytesLE(bytes: Seq[Byte]): Long = bytes.foldRight(0L)((b, sum) => (sum << 8) | (b & 0xFF))
}

object UInt8 {

  private val minValue = 0
  private val maxValue = 255

  def fromByte(byte: Byte): UInt8 = UInt8(byteToShort(byte))

  def byteToShort(byte: Byte): Short = if (byte < 0) (byte.toShort + 256).toShort else byte.toShort

}

case class UInt8(value: Int) extends ToBytes {

  assert(value >= UInt8.minValue)
  assert(value <= UInt8.maxValue)

  def toBytes = List(value.toByte)
}

object UInt16LE {

  private val minValue = 0
  private val maxValue = 65535

  def from(short: Short): UInt16LE = {
    val i = short.toInt
    if (i < 0) UInt16LE(i + 65536) else UInt16LE(i)
  }

  def apply(lsb: Byte, msb: Byte): UInt16LE = UInt16LE(Integers.fromBytesLE(List(lsb, msb)).toInt)
}

case class UInt16LE(value: Int) extends ToBytes {
  assert(value >= UInt16LE.minValue)
  assert(value <= UInt16LE.maxValue)

  def toBytes = Integers.toBytesLE(value, 2)
}

case class SInt16LE(value: Short) extends ToBytes {
  def toBytes = Integers.toBytesLE(value, 2)
}

case class SInt32LE(value: Int) extends ToBytes {
  def toBytes = Integers.toBytesLE(value, 4)
}

object UInt32LE {

  private val minValue = 0
  private val maxValue = 4294967295L
}

case class UInt32LE(value: Long) extends ToBytes {
  assert(value >= UInt32LE.minValue)
  assert(value <= UInt32LE.maxValue)

  def toBytes = Integers.toBytesLE(value, 4)
}

object UInt48LE {

  private val minValue = 0
  private val maxValue = 281474976710655L
}

case class UInt48LE(value: Long) extends ToBytes {
  assert(value >= UInt48LE.minValue)
  assert(value <= UInt48LE.maxValue)

  def toBytes = Integers.toBytesLE(value, 6)
}
