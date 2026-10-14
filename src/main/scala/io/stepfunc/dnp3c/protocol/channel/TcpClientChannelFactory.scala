// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol.channel

import java.io.IOException
import java.net.{ InetSocketAddress, SocketAddress, StandardSocketOptions }
import java.nio.channels.SocketChannel

/**
 * Connects to the DUT over TCP
 *
 * @param local the address and port to connect from, if any: port 0 lets the system choose
 * @param connectTimeoutMs how long a connection may take, if not the system's limit
 */
class TcpClientChannelFactory(
    address: SocketAddress,
    local: Option[InetSocketAddress] = None,
    connectTimeoutMs: Option[Int] = None)
    extends ChannelFactory {

  def open(): SocketReadWriteChannel = {
    val socket = SocketChannel.open()
    try {
      local.foreach(TcpClientChannelFactory.bind(socket, _))
      TcpClientChannelFactory.connect(socket, address, local, connectTimeoutMs.getOrElse(0))
      new SocketReadWriteChannel(socket)
    } catch {
      case ex: Throwable =>
        socket.close()
        throw ex
    }
  }
}

object TcpClientChannelFactory {

  // binds the socket to the local address, reusable so that a port can be bound again soon after a connection from it
  // closes
  def bind(socket: SocketChannel, local: InetSocketAddress): Unit =
    try {
      socket.setOption(StandardSocketOptions.SO_REUSEADDR, java.lang.Boolean.TRUE)
      socket.bind(local)
    } catch {
      case ex: IOException => throw new LocalBindException(local, ex)
    }

  // connects the socket; a failure to use its local address, e.g. one whose port is still held by a connection to the
  // same remote address and port that closed moments ago, is LocalBindException
  def connect(socket: SocketChannel, remote: SocketAddress, local: Option[InetSocketAddress], timeoutMs: Int): Unit =
    try socket.socket().connect(remote, timeoutMs)
    catch {
      case ex: java.net.BindException =>
        throw new LocalBindException(local.getOrElse(new InetSocketAddress(0)), ex)
    }
}

/**
 * The test bench can't do what a procedure needs of it, e.g. use a local address or open a serial port: a problem with
 * the bench or its configuration, not the DUT
 */
class BenchException(message: String, cause: Throwable) extends IOException(message, cause)

/** The harness can't use a local address: it isn't one of this host's, or the port is in use */
class LocalBindException(val local: InetSocketAddress, cause: Throwable)
    extends BenchException(s"Can't use local address $local: ${cause.getMessage}", cause)
