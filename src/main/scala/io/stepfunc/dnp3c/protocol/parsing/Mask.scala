// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol.parsing

case class Mask(mask: Byte) {
  def get(value: Boolean): Byte = if (value) mask else 0
  def isSet(value: Byte): Boolean = (mask & value) != 0
  def apply(value: Byte): Byte = (mask & value).toByte
}

object Mask {
  def build(step: Byte*): Byte = {
    step.foldLeft(0.toByte)((sum, x) => (sum | x).toByte)
  }
}
