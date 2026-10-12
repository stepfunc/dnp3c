// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol

import io.stepfunc.dnp3c.protocol.parsing.UInt16LE

object Hex {

  val prefix = "0x"

  // ---- useful utility functions -----

  def convert(value: Byte): String = String.format("%02X", Byte.box(value))

  def convert(value: UInt16LE): String = String.format("%04X", Int.box(value.value))

  def prefixed(value: Byte): String = prefix + convert(value)

  def prefixed(value: UInt16LE): String = prefix + convert(value)

  def convert(bytes: Iterable[Byte], spaced: Boolean = true, addPrefix: Boolean = false): String = {
    val hex = if (spaced) bytes.map(convert).mkString(" ") else bytes.map(convert).mkString
    if (addPrefix) prefix + hex else hex
  }

  def from(hex: String): List[Byte] = {
    val cleanInput = hex.replaceAll("\\s|\\n", "")
    assert((cleanInput.length % 2) == 0)
    cleanInput.sliding(2, 2).map(s => java.lang.Integer.parseInt(s, 16).toByte).toList
  }

}
