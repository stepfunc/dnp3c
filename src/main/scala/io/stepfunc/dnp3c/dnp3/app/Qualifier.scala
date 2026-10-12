// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import io.stepfunc.dnp3c.protocol.Hex

case class Qualifier(value: Byte, name: String) {
  def hex: String = Hex.prefixed(value)
}

object Qualifier {

  val ONE_BYTE_START_STOP: Qualifier = Qualifier(0x00, "1-Byte Start/Stop")
  val TWO_BYTE_START_STOP: Qualifier = Qualifier(0x01, "2-Byte Start/Stop")

  val ALL_OBJECTS: Qualifier = Qualifier(0x06, "All Objects")

  val ONE_BYTE_COUNT: Qualifier = Qualifier(0x07, "1-Byte Count")
  val TWO_BYTE_COUNT: Qualifier = Qualifier(0x08, "2-Byte Count")

  val ONE_BYTE_COUNT_AND_INDEX: Qualifier = Qualifier(0x17, "1-Byte Count and Index")
  val TWO_BYTE_COUNT_AND_INDEX: Qualifier = Qualifier(0x28, "2-Byte Count and Index")

}
