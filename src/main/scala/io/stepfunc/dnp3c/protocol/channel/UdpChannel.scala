// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol.channel

import java.net.{ DatagramPacket, InetSocketAddress, NetworkInterface, SocketTimeoutException, StandardSocketOptions }
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

/**
 * Exchanges datagrams with the DUT from a local address and port: each write is one datagram to the remote address,
 * and each read one datagram from the remote's IP address, from any port. Datagrams from other addresses, and empty
 * ones, are dropped.
 */
final class UdpChannel private (channel: DatagramChannel, remote: InetSocketAddress) extends ReadableWritableChannel {

  private val received = new Array[Byte](65535)

  // the address and port the DUT's last datagram came from
  @volatile var lastSource: Option[InetSocketAddress] = None

  // the address and port the channel is bound to
  def localAddress: InetSocketAddress = channel.getLocalAddress.asInstanceOf[InetSocketAddress]

  override def datagrams: Boolean = true

  override def write(buffer: ByteBuffer): Unit = {
    channel.send(buffer, remote)
  }

  override def read(buffer: ByteBuffer, timeoutMs: Int): Int = {
    val deadline = System.nanoTime() + timeoutMs * 1000000L
    var result = -1
    while (result < 0) {
      val left = ((deadline - System.nanoTime()) / 1000000L).toInt
      if (left <= 0) throw new SocketTimeoutException("No datagram from the DUT")
      channel.socket().setSoTimeout(left)
      val packet = new DatagramPacket(received, received.length)
      channel.socket().receive(packet)
      // an empty datagram carries nothing, and a read of nothing would end the wait
      if (packet.getAddress == remote.getAddress && packet.getLength > 0) {
        lastSource = Some(packet.getSocketAddress.asInstanceOf[InetSocketAddress])
        buffer.put(received, 0, packet.getLength)
        result = packet.getLength
      }
    }
    result
  }

  override def close(): Unit = channel.close()
}

object UdpChannel {

  /**
   * Binds the local address, exclusively, to talk to the remote; a remote multicast group is sent to from the local
   * address's interface
   */
  def open(remote: InetSocketAddress, local: InetSocketAddress): UdpChannel = {
    val channel = DatagramChannel.open()
    try {
      try channel.bind(local)
      catch { case ex: java.io.IOException => throw new LocalBindException(local, ex) }
      if (remote.getAddress.isMulticastAddress) {
        val interface = Option(NetworkInterface.getByInetAddress(local.getAddress)).getOrElse(
          throw new LocalBindException(local, new Exception("no network interface has this address")))
        channel.setOption(StandardSocketOptions.IP_MULTICAST_IF, interface)
        channel.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, java.lang.Boolean.TRUE)
      }
      new UdpChannel(channel, remote)
    } catch {
      case ex: Throwable =>
        channel.close()
        throw ex
    }
  }
}
