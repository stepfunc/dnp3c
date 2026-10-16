// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3

import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.dnp3.app.{ Message, Unsolicited }
import io.stepfunc.dnp3c.dnp3.link._
import io.stepfunc.dnp3c.dnp3.transport.TransportReader
import io.stepfunc.dnp3c.dnp3
import io.stepfunc.dnp3c.protocol.channel.{
  ChannelFactory, ReadableWritableChannel, SerialChannel, SocketReadWriteChannel, TcpClientChannelFactory, UdpChannel
}
import io.stepfunc.dnp3c.protocol.Clock
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE

import java.io.IOException
import java.net.{ ConnectException, InetSocketAddress, SocketTimeoutException }
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import scala.collection.mutable

class DefaultTestDriver(
    val reporter: ProcedureReporter,
    private var factory: ChannelFactory,
    localAddr: UInt16LE,
    remoteAddr: UInt16LE,
    defaultLinkTimeoutMs: Int,
    appTimeoutMs: Int,
    unsolTimeoutMs: Int,
    // added to the response timeouts, for the time a response takes to arrive
    timingToleranceMs: Int,
    val clock: Clock = Clock.system,
    override val policies: Policies = Policies.none) extends TestDriver {

  private case class Connection(
      channel: ReadableWritableChannel,
      parser: LinkFrameParser,
      messageReader: TransportReader)

  private object Connection {

    def apply(factory: ChannelFactory): Connection = apply(factory.open())

    def apply(channel: ReadableWritableChannel): Connection = {
      val parser = new LinkFrameParser(channel, clock)
      val frameReader = new ValidatingLinkFrameReader(reporter, parser, local, remote, clock)(writeFrame)
      val fragmentReader = new TransportReader(frameReader, reporter, clock)
      Connection(channel, parser, fragmentReader)
    }
  }

  private var connection: Option[Connection] = None

  private def getConnection[A](fun: Connection => A): A = connection match {
    case Some(x) => fun(x)
    case None => {
      val conn = Connection(factory)
      this.connection = Some(conn)
      fun(conn)
    }
  }

  override def write(bytes: List[Byte]): Unit = getConnection { c =>
    writeToChannel(c)(bytes)
  }

  // a UDP socket isn't a connection: it stays bound, so that what the DUT sends while it restarts arrives
  override def close(): Unit = {
    connection.filterNot(_.channel.datagrams).foreach { conn =>
      conn.channel.close()
      this.connection = None
    }
  }

  override def shutdown(): Unit = {
    connection.foreach(_.channel.close())
    connection = None
    kept.values.foreach(_._1.channel.close())
    kept.clear()
  }

  // the connection that connect made current, if it did, and those it kept open, by what was asked for, each with the
  // factory that opens it again
  private var current: Option[dnp3.Connection] = None
  private val kept = mutable.Map.empty[dnp3.Connection, (Connection, ChannelFactory)]

  private def localOf(c: Connection): InetSocketAddress = c.channel match {
    case tcp: SocketReadWriteChannel => tcp.localAddress
    case udp: UdpChannel => udp.localAddress
    case other => throw new IllegalStateException(s"No local address for $other")
  }

  override def connect(c: dnp3.Connection, keep: Boolean): InetSocketAddress = {
    (current, connection) match {
      case (Some(key), Some(open)) if keep => kept(key) = (open, factory)
      case _ => connection.foreach(_.channel.close())
    }
    connection = None
    current = Some(c)
    kept.remove(c) match {
      case Some((open, f)) =>
        connection = Some(open)
        factory = f
        localOf(open)
      case None => open(c)
    }
  }

  override def drainDatagrams(c: UdpConnection): Int = {
    val open = if (current.contains(c)) connection else kept.get(c).map(_._1)
    open.map(_.channel) match {
      case Some(udp: UdpChannel) => udp.drain()
      case _ => throw new IllegalStateException(s"No UDP connection is open as $c")
    }
  }

  private def open(c: dnp3.Connection): InetSocketAddress = {
    val (opened, local) = c match {
      case tcp: TcpConnection =>
        val f = new TcpClientChannelFactory(tcp.remote, Some(tcp.local), Some(appTimeoutMs))
        val channel = f.open()
        factory = f
        (channel, channel.localAddress)
      case udp: UdpConnection =>
        val channel = UdpChannel.open(udp.remote, udp.local)
        // a reconnection binds the same port again
        val bound = channel.localAddress
        factory = () => UdpChannel.open(udp.remote, bound)
        (channel, bound)
    }
    connection = Some(Connection(opened))
    local
  }

  override def sendDatagrams(
      remote: InetSocketAddress,
      local: InetSocketAddress,
      message: Message,
      options: LinkOverride): Unit = {
    val channel = UdpChannel.open(remote, local)
    try {
      toFrames(message, options) { frame =>
        val bytes = frame.toBytes
        reporter.transmit(bytes)
        channel.write(ByteBuffer.wrap(bytes.toArray))
      }
    } finally channel.close()
  }

  override def probe(c: TcpConnection): ProbeResult = {
    val socket = SocketChannel.open()
    try {
      TcpClientChannelFactory.bind(socket, c.local)
      val accepted =
        try {
          TcpClientChannelFactory.connect(socket, c.remote, Some(c.local), appTimeoutMs)
          None
        } catch {
          case _: ConnectException => Some(ProbeResult.Refused)
          case _: SocketTimeoutException => Some(ProbeResult.TimedOut)
        }
      accepted.getOrElse {
        val channel = new SocketReadWriteChannel(socket)
        val request = LinkFrame.headerOnly(Control(0xC9.toByte), remote, local)
        try {
          reporter.transmit(request)
          channel.write(ByteBuffer.wrap(request.toBytes.toArray))
          new LinkFrameParser(channel, clock).read(reporter)(defaultLinkTimeoutMs) match {
            case Some(_) => ProbeResult.Answered
            case None => ProbeResult.Silent
          }
        } catch {
          case _: IOException => ProbeResult.Closed
        }
      }
    } finally socket.close()
  }

  def local: UInt16LE = localAddr

  def remote: UInt16LE = remoteAddr

  private def writeToChannel(connection: Connection)(bytes: List[Byte]): Unit = {
    reporter.transmit(bytes)
    connection.channel.write(ByteBuffer.wrap(bytes.toArray))
  }

  override def writeMessage(message: Message, options: LinkOverride): Unit = getConnection { c =>
    toFrames(message, options)(frame => writeToChannel(c)(frame.toBytes))
  }

  override def writeFrame(frame: LinkFrame): Unit = getConnection { c =>
    reporter.transmit(frame)
    writeToChannel(c)(frame.toBytes)
  }

  override def readMessage(
      unsolicited: Unsolicited,
      validator: FrameValidator,
      linkOverride: LinkOverride,
      timeoutMs: Option[Int]): Option[Message] = getConnection { c =>
    val timeout =
      timeoutMs.getOrElse((if (unsolicited.value) unsolTimeoutMs else appTimeoutMs) + timingToleranceMs)
    val message = c.messageReader.read(timeout, validator, linkOverride).map(Message.parse)
    message.foreach(reporter.receive)
    message.foreach(policies.check)
    message
  }

  override def readFrameTimed(timeout: Option[Int] = None): Option[(LinkFrame, Long)] = getConnection { c =>
    c.parser.read(reporter)(timeout.getOrElse(defaultLinkTimeoutMs)).map(f => (f, c.parser.lastFrameStart))
  }

  override def setRts(asserted: Boolean): Unit = getConnection { c =>
    c.channel match {
      case serial: SerialChannel => serial.setRts(asserted)
      case _ => throw new IllegalStateException("RTS needs a serial connection (serial.enabled)")
    }
  }

  override def readFrame(timeout: Option[Int] = None): Option[LinkFrame] = getConnection { c =>
    val frame = c.parser.read(reporter)(timeout.getOrElse(defaultLinkTimeoutMs))
    if (frame.isEmpty) reporter.warn("frame receive timeout")
    frame
  }

}
