// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol.channel

import java.net.SocketAddress
import java.nio.channels.SocketChannel

class TcpClientChannelFactory(address: SocketAddress) extends ChannelFactory {
  def open(): ReadableWritableChannel = {
    val socket = SocketChannel.open(address)
    new SocketReadWriteChannel(socket)
  }
}
