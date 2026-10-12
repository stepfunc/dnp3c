// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol.parsing

/**
 * Anything that can be serialized to a List[Byte]
 */
trait ToBytes {
  def toBytes: List[Byte]
}
