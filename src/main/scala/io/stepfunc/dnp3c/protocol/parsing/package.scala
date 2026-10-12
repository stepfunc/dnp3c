// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol

package object parsing {

  implicit def convert(bytes: ToBytes): List[Byte] = bytes.toBytes

  def join(items: List[Byte]*): List[Byte] = items.flatten.toList

}
