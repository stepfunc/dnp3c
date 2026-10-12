// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol.channel

import java.nio.ByteBuffer

trait WritableChannel {

  // write all data or except
  def write(buffer: ByteBuffer): Unit
}
