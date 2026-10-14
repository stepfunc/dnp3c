// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.link

import io.stepfunc.dnp3c.dnp3.TestReporter
import io.stepfunc.dnp3c.protocol.channel.ReadableChannel
import io.stepfunc.dnp3c.protocol.parsing.{ UInt16LE, UInt8 }

import io.stepfunc.dnp3c.protocol.Clock

import java.io.EOFException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import scala.annotation.tailrec
import scala.collection.mutable

object LinkFrameParser {

  // what the start of the received bytes holds
  sealed trait Decoded

  object Decoded {
    // a frame, which is the first length bytes
    final case class Frame(frame: LinkFrame, length: Int) extends Decoded

    // the start of what may be a frame: more bytes are needed
    case object Incomplete extends Decoded

    // count bytes to drop: noise before the start of a frame, or a frame that's corrupt
    final case class Skip(count: Int) extends Decoded
  }

  private val headerSize = 10

  def decode(bytes: collection.IndexedSeq[Byte], reporter: TestReporter): Decoded = {
    // a frame starts 05 64; a trailing 05 may be the start of one
    val start = bytes.indices.find(i => bytes(i) == 0x05 && (i + 1 == bytes.size || bytes(i + 1) == 0x64))
    start match {
      case None => if (bytes.isEmpty) Decoded.Incomplete else Decoded.Skip(bytes.size)
      case Some(i) if i > 0 => Decoded.Skip(i)
      case Some(_) => if (bytes.size < headerSize) Decoded.Incomplete else decodeFrame(bytes, reporter)
    }
  }

  // bytes start with a complete header
  private def decodeFrame(bytes: collection.IndexedSeq[Byte], reporter: TestReporter): Decoded = {
    val header = ByteBuffer.wrap(bytes.slice(2, headerSize).toArray)
    val len = UInt8.fromByte(header.get())
    val ctrl = Control(header.get())
    val dest = UInt16LE.from(java.lang.Short.reverseBytes(header.getShort()))
    val src = UInt16LE.from(java.lang.Short.reverseBytes(header.getShort()))
    val crc = java.lang.Short.reverseBytes(header.getShort())
    val hdr = LinkHeader(len, ctrl, dest, src)

    if (crc != hdr.crc) {
      reporter.warn("Crc failure in header: " + hdr.toString)
      Decoded.Skip(2) // search for the next start
    } else if (len.value < 5) {
      reporter.warn("Invalid length: " + len.value)
      Decoded.Skip(headerSize)
    } else {
      val dataSize = len.value - 5
      val total = headerSize + calcTotalSize(dataSize)
      if (bytes.size < total) Decoded.Incomplete
      else
        extractData(dataSize, ByteBuffer.wrap(bytes.slice(headerSize, total).toArray), reporter) match {
          case Some(data) => Decoded.Frame(LinkFrame(hdr, data), total)
          case None => Decoded.Skip(total) // crc failure in the body
        }
    }
  }

  def calcTotalSize(dataSize: Int): Int = {
    val numFull = dataSize / 16
    val mod16 = dataSize % 16
    val hasPartial = mod16 != 0
    numFull * 18 + (if (hasPartial) (mod16 + 2) else 0)
  }

  def extractData(dataSize: Int, buffer: ByteBuffer, reporter: TestReporter): Option[List[Byte]] = {

    @tailrec
    def recurse(running: List[Byte], remaining: Int, buffer: ByteBuffer): Option[List[Byte]] = {
      if (remaining == 0) Some(running)
      else {
        val num = math.min(16, remaining)
        val array = new Array[Byte](num)
        buffer.get(array, 0, num)
        val segment = array.toList
        val data = running ++ segment
        val crc = java.lang.Short.reverseBytes(buffer.getShort())
        if (Crc.calc(segment) == crc) {
          recurse(data, remaining - num, buffer)
        } else {
          reporter.warn("Crc failure in body")
          None
        }
      }
    }

    recurse(Nil, dataSize, buffer)
  }

}

class LinkFrameParser(channel: ReadableChannel, clock: Clock) {

  // bytes received but not yet decoded
  private val received = mutable.ArrayBuffer.empty[Byte]
  private val buffer = ByteBuffer.allocate(if (channel.datagrams) 65535 else 4096)

  // the frames of the last datagram not yet read, each with when its first byte arrived
  private val frames = mutable.Queue.empty[(LinkFrame, Long)]

  // when the received bytes arrived: how many came in each read, and the clock's time then, in nanoseconds
  private val arrivals = mutable.Queue.empty[(Int, Long)]

  // when the first byte of the last frame read arrived, by the clock, in nanoseconds
  private var frameStart = 0L

  /** when the first byte of the frame the last read returned arrived, by the clock, in nanoseconds */
  def lastFrameStart: Long = frameStart

  // drops the first count received bytes, and returns when the first of them arrived
  private def consume(count: Int): Long = {
    val first = arrivals.headOption.map(_._2).getOrElse(clock.nanoTime())
    received.remove(0, count)
    var left = count
    while (left > 0 && arrivals.nonEmpty) {
      val (n, at) = arrivals.dequeue()
      if (n > left) arrivals.prepend((n - left, at))
      left -= n
    }
    first
  }

  private def clearReceived(): Unit = {
    received.clear()
    arrivals.clear()
  }

  // the next frame, read until one arrives or the timeout runs out; a frame can arrive over several reads, unless the
  // channel's reads are datagrams
  def read(reporter: TestReporter)(timeoutMs: Int): Option[LinkFrame] =
    if (channel.datagrams) readDatagrams(reporter)(timeoutMs) else readStream(reporter)(timeoutMs)

  // each read is a whole datagram, decoded at once, and IEEE 1815-2012 13.2.3.2.3 keeps a frame within one: one that
  // doesn't end in its datagram fails, even after frames that do
  private def readDatagrams(reporter: TestReporter)(timeoutMs: Int): Option[LinkFrame] = {
    val deadline = clock.deadline(timeoutMs)

    @tailrec
    def decodeAll(): Unit = LinkFrameParser.decode(received, reporter) match {
      case LinkFrameParser.Decoded.Frame(frame, length) =>
        frames.enqueue((frame, consume(length)))
        decodeAll()
      case LinkFrameParser.Decoded.Skip(count) =>
        consume(count)
        decodeAll()
      case LinkFrameParser.Decoded.Incomplete =>
        if (received.nonEmpty) {
          val count = received.size
          clearReceived()
          frames.clear()
          throw new Exception(
            s"A datagram from the DUT ends with $count byte(s) of a link frame that doesn't end there")
        }
    }

    @tailrec
    def next(): Option[LinkFrame] =
      if (frames.nonEmpty) {
        val (frame, start) = frames.dequeue()
        frameStart = start
        reporter.receive(frame)
        Some(frame)
      } else {
        val left = clock.msLeft(deadline)
        if (left > 0 && receive(left.toInt)) {
          decodeAll()
          next()
        } else None
      }

    next()
  }

  private def readStream(reporter: TestReporter)(timeoutMs: Int): Option[LinkFrame] = {
    val deadline = clock.deadline(timeoutMs)

    @tailrec
    def next(): Option[LinkFrame] = LinkFrameParser.decode(received, reporter) match {
      case LinkFrameParser.Decoded.Frame(frame, length) =>
        frameStart = consume(length)
        reporter.receive(frame)
        Some(frame)
      case LinkFrameParser.Decoded.Skip(count) =>
        consume(count)
        next()
      case LinkFrameParser.Decoded.Incomplete =>
        val left = clock.msLeft(deadline)
        if (left > 0 && receive(left.toInt)) next() else None
    }

    next()
  }

  // false if nothing arrived before the timeout
  private def receive(timeoutMs: Int): Boolean = {
    buffer.clear()
    val num =
      try channel.read(buffer, timeoutMs)
      catch { case _: SocketTimeoutException => 0 }
    if (num < 0) throw new EOFException("The DUT closed the connection")
    buffer.flip()
    if (buffer.hasRemaining) arrivals.enqueue((buffer.remaining(), channel.lastReadAt.getOrElse(clock.nanoTime())))
    while (buffer.hasRemaining) received += buffer.get()
    num > 0
  }
}
