// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.link

import io.stepfunc.dnp3c.protocol.parsing._

case class LinkHeader(len: UInt8, ctrl: Control, dest: UInt16LE, src: UInt16LE) extends ToBytes {

  private def bytes: List[Byte] = LinkFrame.sync ::: bytesNoSync

  def bytesNoSync: List[Byte] = join(len, ctrl, dest, src)

  def toBytesWithGen(gen: CrcGenerator) = gen.append(bytes)

  override def toBytes = toBytesWithGen(Crc)

  def crc = Crc.calc(bytes)

  override def toString: String = ctrl.toString + " length: " + len.value + " dest: " + dest.value + " src: " +
    src.value
}

case class LinkFrame(header: LinkHeader, data: List[Byte]) extends ToBytes {

  def toBytesWithGen(headerCrc: CrcGenerator, bodyCrc: CrcGenerator) = header.toBytesWithGen(headerCrc) ++
    bodyCrc.interlace(data)

  def toBytesWithStart(first: Byte, second: Byte) = Crc.append(first :: second :: header.bytesNoSync) ++
    Crc.interlace(data)

  override def toBytes = header.toBytes ++ Crc.interlace(data)

  override def toString = header.toString

}

object LinkFrame {

  def withData(ctrl: Control, dest: UInt16LE, src: UInt16LE, data: List[Byte]): LinkFrame = {
    val len = UInt8((data.size + 5).toShort)
    LinkFrame(LinkHeader(len, ctrl, dest, src), data)
  }

  def apply(header: LinkHeader): LinkFrame = LinkFrame(header, List.empty[Byte])

  // a frame with no user data, e.g. an ACK
  def headerOnly(ctrl: Control, dest: UInt16LE, src: UInt16LE): LinkFrame = withData(ctrl, dest, src, Nil)

  val sync = List(0x05.toByte, 0x64.toByte)

}
