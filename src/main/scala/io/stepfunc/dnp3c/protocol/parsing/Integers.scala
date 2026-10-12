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
}

object UInt8 {

  private val minValue = 0
  private val maxValue = 255

  val min = UInt8(minValue)
  val max = UInt8(maxValue)

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

  val min = UInt16LE(minValue)
  val max = UInt16LE(maxValue)

  def from(short: Short): UInt16LE = {
    val i = short.toInt
    if (i < 0) UInt16LE(i + 65536) else UInt16LE(i)
  }

  def apply(lsb: Byte, msb: Byte): UInt16LE = {
    val b1 = UInt8.byteToShort(lsb)
    val b2 = UInt8.byteToShort(msb)

    UInt16LE((b2.toInt << 8) | b1.toInt)
  }
}

case class UInt16LE(value: Int) extends ToBytes {
  assert(value >= UInt16LE.minValue)
  assert(value <= UInt16LE.maxValue)

  def toBytes = Integers.toBytesLE(value, 2)
}

object SInt16LE {
  def apply(lsb: Byte, msb: Byte): SInt16LE = {
    val b1 = UInt8.byteToShort(lsb)
    val b2 = UInt8.byteToShort(msb)
    SInt16LE(((b2.toShort << 8) | b1.toShort).toShort)
  }
}

case class SInt16LE(value: Short) extends ToBytes {
  def toBytes = Integers.toBytesLE(value, 2)
}

object SInt32LE {

  def apply(b1: Byte, b2: Byte, b3: Byte, b4: Byte): SInt32LE = {
    val sb1 = UInt8.byteToShort(b1)
    val sb2 = UInt8.byteToShort(b2)
    val sb3 = UInt8.byteToShort(b3)
    val sb4 = UInt8.byteToShort(b4)
    SInt32LE((sb4.toInt << 24) | (sb3.toInt << 16) | (sb2.toInt << 8) | sb1.toInt)
  }

}

case class SInt32LE(value: Int) extends ToBytes {
  def toBytes = Integers.toBytesLE(value, 4)
}

object UInt32LE {

  private val minValue = 0
  private val maxValue = 4294967295L

  val min = UInt32LE(minValue)
  val max = UInt32LE(maxValue)

  def apply(b1: Byte, b2: Byte, b3: Byte, b4: Byte): UInt32LE = {
    val sb1 = UInt8.byteToShort(b1)
    val sb2 = UInt8.byteToShort(b2)
    val sb3 = UInt8.byteToShort(b3)
    val sb4 = UInt8.byteToShort(b4)
    UInt32LE((sb4.toLong << 24) | (sb3.toLong << 16) | (sb2.toLong << 8) | sb1.toLong)
  }
}

case class UInt32LE(value: Long) extends ToBytes {
  assert(value >= UInt32LE.minValue)
  assert(value <= UInt32LE.maxValue)

  def toBytes = Integers.toBytesLE(value, 4)
}

object UInt48LE {

  private val minValue = 0
  private val maxValue = 281474976710655L

  val min = UInt48LE(minValue)
  val max = UInt48LE(maxValue)

  def apply(b1: Byte, b2: Byte, b3: Byte, b4: Byte, b5: Byte, b6: Byte): UInt48LE = {
    val sb1 = UInt8.byteToShort(b1)
    val sb2 = UInt8.byteToShort(b2)
    val sb3 = UInt8.byteToShort(b3)
    val sb4 = UInt8.byteToShort(b4)
    val sb5 = UInt8.byteToShort(b5)
    val sb6 = UInt8.byteToShort(b6)
    UInt48LE((sb6.toLong << 40) | (sb5.toLong << 32) | (sb4.toLong << 24) | (sb3.toLong << 16) | (sb2.toLong << 8) |
      sb1.toLong)
  }
}

case class UInt48LE(value: Long) extends ToBytes {
  assert(value >= UInt48LE.minValue)
  assert(value <= UInt48LE.maxValue)

  def toBytes = Integers.toBytesLE(value, 6)
}
