// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.link

import java.io.EOFException
import java.nio.ByteBuffer

import io.stepfunc.dnp3c.dnp3.TestReporter
import io.stepfunc.dnp3c.dnp3.link.LinkFrameParser.Decoded
import io.stepfunc.dnp3c.protocol.VirtualClock
import io.stepfunc.dnp3c.protocol.Hex
import io.stepfunc.dnp3c.protocol.channel.ReadableChannel
import io.stepfunc.dnp3c.protocol.logging.Logger
import io.stepfunc.dnp3c.protocol.parsing.{ UInt16LE, UInt8 }
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class LinkFrameParserTest extends AnyFunSuite with Matchers {

  private val reporter = TestReporter(Logger.empty)

  // a header-only frame: RESET LINK STATES from 1024 to 1
  private val reset = Hex.from("05 64 05 C0 01 00 00 04 E9 21")
  private val resetFrame =
    LinkFrame(LinkHeader(
      UInt8(5),
      Control(true, false, false, LinkFunction.RESET_LINK_STATES),
      UInt16LE(1),
      UInt16LE(1024)))

  // confirmed user data with one body block
  private val userData = Hex.from("05 64 14 F3 01 00 00 04 0A 3B C0 C3 01 3C 02 06 3C 03 06 3C 04 06 3C 01 06 9A 12")
  private val userDataFrame = LinkFrame(
    LinkHeader(UInt8(20), Control(true, true, true, LinkFunction.CONFIRMED_USER_DATA), UInt16LE(1), UInt16LE(1024)),
    Hex.from("C0 C3 01 3C 02 06 3C 03 06 3C 04 06 3C 01 06")
  )

  private def decode(bytes: List[Byte]): Decoded = LinkFrameParser.decode(bytes.toIndexedSeq, reporter)

  // hands over each chunk in its own read, then reports a timeout, or the end of the stream
  private class Chunks(chunks: List[Byte]*)(eof: Boolean = false) extends ReadableChannel {
    private var pending = chunks.toList
    override def read(buffer: ByteBuffer, timeoutMs: Int): Int = pending match {
      case chunk :: rest =>
        pending = rest
        buffer.put(chunk.toArray)
        chunk.size
      case Nil => if (eof) -1 else 0
    }
  }

  private def frames(channel: ReadableChannel): List[LinkFrame] = {
    val parser = new LinkFrameParser(channel, new VirtualClock)
    Iterator.continually(parser.read(reporter)(1000)).takeWhile(_.isDefined).flatten.toList
  }

  test("decodes a header-only frame and a frame with user data") {
    decode(reset) should equal(Decoded.Frame(resetFrame, 10))
    decode(userData) should equal(Decoded.Frame(userDataFrame, userData.size))
  }

  test("needs more bytes for a partial header or body") {
    decode(Nil) should equal(Decoded.Incomplete)
    decode(reset.take(4)) should equal(Decoded.Incomplete)
    decode(userData.take(11)) should equal(Decoded.Incomplete)
  }

  test("skips noise before the start of a frame, keeping a trailing 05 that may start one") {
    decode(Hex.from("01 02 05 64") ::: reset.drop(2)) should equal(Decoded.Skip(2))
    decode(Hex.from("01 02 03")) should equal(Decoded.Skip(3))
    decode(Hex.from("01 05")) should equal(Decoded.Skip(1))
  }

  test("skips a frame with a bad CRC") {
    decode(reset.updated(9, 0x20.toByte)) should equal(Decoded.Skip(2))
    decode(userData.updated(userData.size - 1, 0x1F.toByte)) should equal(Decoded.Skip(userData.size))
  }

  test("reads a frame that arrives over several reads") {
    frames(new Chunks(userData.take(3), userData.slice(3, 11), userData.drop(11))()) should equal(List(userDataFrame))
  }

  test("reads frames that share a read, and one split between reads") {
    val bytes = reset ::: userData ::: reset
    frames(new Chunks(bytes.take(15), bytes.drop(15))()) should equal(List(resetFrame, userDataFrame, resetFrame))
  }

  test("resynchronizes after noise and a corrupt frame, wherever they fall in the reads") {
    val bytes = Hex.from("FF 05 00") ::: reset.updated(9, 0x20.toByte) ::: userData
    for (split <- 1 until bytes.size)
      withClue(s"split at $split: ")(frames(new Chunks(bytes.take(split), bytes.drop(split))()) should equal(List(
        userDataFrame)))
  }

  test("reads a maximum-size frame split across reads") {
    val frame = LinkFrame.withData(
      Control(false, false, false, LinkFunction.UNCONFIRMED_USER_DATA),
      UInt16LE(1),
      UInt16LE(1024),
      List.tabulate(250)(_.toByte))
    val bytes = frame.toBytes
    bytes.size should equal(292)
    frames(new Chunks(bytes.take(100), bytes.slice(100, 200), bytes.drop(200))()) should equal(List(frame))
  }

  test("reads a valid frame after one with a bad body CRC") {
    val corrupt = userData.updated(userData.size - 1, 0x1F.toByte)
    frames(new Chunks(corrupt.take(20), corrupt.drop(20) ::: reset)()) should equal(List(resetFrame))
  }

  test("reports a timeout when nothing, or only part of a frame, arrives") {
    frames(new Chunks()()) should equal(Nil)
    frames(new Chunks(userData.take(20))()) should equal(Nil)
  }

  test("fails when the DUT closes the connection, rather than reporting silence") {
    an[EOFException] should be thrownBy frames(new Chunks(reset.take(4))(eof = true))
    frames(new Chunks(reset)(eof = false)) should equal(List(resetFrame))
  }
}
