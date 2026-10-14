// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol.channel

import java.nio.ByteBuffer

trait ReadableChannel {

  // read some amount of data before the timeout occurs
  def read(buffer: ByteBuffer, timeoutMs: Int): Int

  // whether each read is one whole datagram, of at most 65535 bytes, rather than part of a stream
  def datagrams: Boolean = false
}
