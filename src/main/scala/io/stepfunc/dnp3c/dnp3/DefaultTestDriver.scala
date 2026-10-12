// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3

import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.dnp3.app.{ Message, Unsolicited }
import io.stepfunc.dnp3c.dnp3.link._
import io.stepfunc.dnp3c.dnp3.transport.{ SegmentWriter, TransportReader }
import io.stepfunc.dnp3c.protocol.channel.{ ChannelFactory, ReadableWritableChannel }
import io.stepfunc.dnp3c.protocol.Clock
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE

import java.nio.ByteBuffer

class DefaultTestDriver(
    val reporter: ProcedureReporter,
    factory: ChannelFactory,
    localAddr: UInt16LE,
    remoteAddr: UInt16LE,
    defaultLinkTimeoutMs: Int,
    appTimeoutMs: Int,
    unsolTimeoutMs: Int,
    val clock: Clock = Clock.system) extends TestDriver {

  private case class Connection(
      channel: ReadableWritableChannel,
      parser: LinkFrameParser,
      messageReader: TransportReader)

  private object Connection {

    def apply(factory: ChannelFactory): Connection = {
      val channel = factory.open()
      val parser = new LinkFrameParser(channel, clock)
      val frameReader = new ValidatingLinkFrameReader(reporter, parser, local, remote, clock)(writeFrame)
      val fragmentReader = new TransportReader(frameReader, reporter, clock)
      Connection(channel, parser, fragmentReader)
    }
  }

  // added to the configured response timeouts, for the time a response takes to arrive
  private val readToleranceMs = 200

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

  override def close(): Unit = {
    connection.foreach { conn =>
      conn.channel.close()
      this.connection = None
    }
  }

  def local: UInt16LE = localAddr

  def remote: UInt16LE = remoteAddr

  private def writeToChannel(connection: Connection)(bytes: List[Byte]): Unit = {
    reporter.transmit(bytes)
    connection.channel.write(ByteBuffer.wrap(bytes.toArray))
  }

  override def writeMessage(message: Message, options: LinkOverride): Unit = getConnection { c =>
    reporter.transmit(message)
    SegmentWriter.write(message.toBytes, 0, reporter) { segment =>
      LinkWriter.write(
        local,
        options.destination.getOrElse(remote),
        true,
        options.controlBlock,
        reporter)(segment)(writeToChannel(c))
    }
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
    val timeout = timeoutMs.getOrElse((if (unsolicited.value) unsolTimeoutMs else appTimeoutMs) + readToleranceMs)
    val message = c.messageReader.read(timeout, validator, linkOverride).map(Message.parse)
    message.foreach(reporter.receive)
    message
  }

  override def readFrame(timeout: Option[Int] = None): Option[LinkFrame] = getConnection { c =>
    val frame = c.parser.read(reporter)(timeout.getOrElse(defaultLinkTimeoutMs))
    if (frame.isEmpty) reporter.warn("frame receive timeout")
    frame
  }

}
