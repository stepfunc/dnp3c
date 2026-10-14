// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.Index
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.protocol.parsing.{ UInt16LE, UInt8 }

case class CommandConfig[T <: FixedSizeSerializable](index: Index, command: T) {

  case class Mods(index: Option[Index], modify: T => T)

  object Mods {
    val none: Mods = Mods(None, x => x)
    def apply(index: Index): Mods = Mods(Some(index), x => x)
    def apply(fun: T => T): Mods = Mods(None, fun)
  }

  def oneByteHeader: OneByteCountAndIndexHeader = oneByteHeader(Mods.none)
  def twoByteHeader: TwoByteCountAndIndexHeader = twoByteHeader(Mods.none)

  def oneByteHeader(modify: T => T): OneByteCountAndIndexHeader = oneByteHeader(Mods(modify))
  def twoByteHeader(modify: T => T): TwoByteCountAndIndexHeader = twoByteHeader(Mods(modify))

  def oneByteHeader(index: Index): OneByteCountAndIndexHeader = oneByteHeader(Mods(index))
  def oneByteHeader(index: Index, modify: T => T): OneByteCountAndIndexHeader = oneByteHeader(Mods(Some(index), modify))

  def twoByteHeader(index: Index): TwoByteCountAndIndexHeader = twoByteHeader(Mods(index))
  def twoByteHeader(index: Index, modify: T => T): TwoByteCountAndIndexHeader = twoByteHeader(Mods(Some(index), modify))

  private def oneByteHeader(mods: Mods): OneByteCountAndIndexHeader = {
    OneByteCountAndIndexHeader(
      command.variation,
      UInt8(1),
      PrefixedPayload.U8(List(Prefix(UInt8(mods.index.getOrElse(index).value), mods.modify(command).toFixedSizeObject)))
    )
  }

  private def twoByteHeader(mods: Mods): TwoByteCountAndIndexHeader = {
    TwoByteCountAndIndexHeader(
      command.variation,
      UInt16LE(1),
      PrefixedPayload.U16(
        List(Prefix(UInt16LE(mods.index.getOrElse(index).value), mods.modify(command).toFixedSizeObject))))
  }

}
