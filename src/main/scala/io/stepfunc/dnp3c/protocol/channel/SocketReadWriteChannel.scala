// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol.channel

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{ Channels, SocketChannel }
import scala.annotation.tailrec

final class SocketReadWriteChannel(channel: SocketChannel) extends ReadableWritableChannel {

  val wrappedChannel = Channels.newChannel(channel.socket().getInputStream)

  @tailrec
  override def write(buffer: ByteBuffer): Unit = {
    if (buffer.remaining() > 0) {
      channel.write(buffer)
      write(buffer)
    }
  }

  override def read(buffer: ByteBuffer, timeoutMs: Int): Int = {
    channel.socket().setSoTimeout(timeoutMs)
    wrappedChannel.read(buffer)
  }

  // the address and port the connection is from
  def localAddress: InetSocketAddress = channel.getLocalAddress.asInstanceOf[InetSocketAddress]

  override def close(): Unit = {
    channel.close()
  }
}
