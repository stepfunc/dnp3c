// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol.channel

/**
 * Provides a mechanism for opening a read/write channel
 */
trait ChannelFactory {
  def open(): ReadableWritableChannel
}
