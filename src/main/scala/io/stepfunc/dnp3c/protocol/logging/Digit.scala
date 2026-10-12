// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol.logging

object Digit {
  def apply(value: Boolean): Int = if (value) 1 else 0
}
