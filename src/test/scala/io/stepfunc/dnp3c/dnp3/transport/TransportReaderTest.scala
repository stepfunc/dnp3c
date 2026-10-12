// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.transport

import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.dnp3.{ LinkOverride, TestReporter }
import io.stepfunc.dnp3c.dnp3.link.LinkFrameReader
import io.stepfunc.dnp3c.protocol.VirtualClock
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class TransportReaderTest extends AnyFunSuite with Matchers {

  class MockLinkReader extends LinkFrameReader {

    val queue = collection.mutable.Queue.empty[List[Byte]]

    override def read(validator: FrameValidator, linkOverride: LinkOverride)(timeoutMs: Long): Option[List[Byte]] = {
      if (queue.isEmpty) None
      else Some(queue.dequeue())
    }

  }

  val data: List[Byte] = Hex.from("DE AD BE EF")
  val fullSegment: List[Byte] = List.fill(249)(0xFF.toByte)

  test("correctly parses fir-fin packet") {
    val frameReader = new MockLinkReader
    val messageReader = new TransportReader(frameReader, TestReporter.empty, new VirtualClock)
    frameReader.queue.enqueue(Transport.function(true, true, 0) :: data)
    messageReader.read(500) should equal(Some(data))
  }

  test("throws on non-fir packet") {
    val frameReader = new MockLinkReader
    val messageReader = new TransportReader(frameReader, TestReporter.empty, new VirtualClock)
    frameReader.queue.enqueue(Transport.function(false, true, 0) :: data)
    intercept[Exception](messageReader.read(500))
  }

  test("correctly parses 3 segment message") {
    val frameReader = new MockLinkReader
    val messageReader = new TransportReader(frameReader, TestReporter.empty, new VirtualClock)
    frameReader.queue.enqueue(Transport.function(true, false, 0) :: fullSegment)
    frameReader.queue.enqueue(Transport.function(false, false, 1) :: fullSegment)
    frameReader.queue.enqueue(Transport.function(false, true, 2) :: data)
    messageReader.read(500) should equal(Some(fullSegment ::: fullSegment ::: data))
  }

  test("a timeout before any segment is silence, but one after a FIR segment fails") {
    val frameReader = new MockLinkReader
    val messageReader = new TransportReader(frameReader, TestReporter.empty, new VirtualClock)
    messageReader.read(500) should equal(None)
    frameReader.queue.enqueue(Transport.function(true, false, 0) :: fullSegment)
    intercept[Exception](messageReader.read(500))
  }

  test("Excepts when out of sequence segment arrives skips when new fir arrives") {
    val frameReader = new MockLinkReader
    val messageReader = new TransportReader(frameReader, TestReporter.empty, new VirtualClock)
    frameReader.queue.enqueue(Transport.function(true, false, 0) :: fullSegment)
    frameReader.queue.enqueue(Transport.function(true, true, 4) :: List(0xFF.toByte))
    intercept[Exception](messageReader.read(500))
  }

}
