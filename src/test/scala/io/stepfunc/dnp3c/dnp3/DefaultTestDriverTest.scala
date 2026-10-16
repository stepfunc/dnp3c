// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3

import java.nio.ByteBuffer

import io.stepfunc.dnp3c.dnp3.app.Unsolicited
import io.stepfunc.dnp3c.dnp3.link.{ Control, LinkFrame, LinkFunction }
import io.stepfunc.dnp3c.protocol.VirtualClock
import io.stepfunc.dnp3c.protocol.Hex
import io.stepfunc.dnp3c.protocol.channel.{ ChannelFactory, ReadableWritableChannel }
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class DefaultTestDriverTest extends AnyFunSuite with Matchers {

  // a null response (transport header C0, then C0 81 00 00) in a CONFIRMED_USER_DATA frame with the given FCB
  private def response(fcb: Boolean): List[Byte] = LinkFrame
    .withData(
      Control(false, fcb, true, LinkFunction.CONFIRMED_USER_DATA),
      UInt16LE(1),
      UInt16LE(1024),
      Hex.from("C0 C0 81 00 00"))
    .toBytes

  // each connection the factory opens replays its own bytes once
  private class Factory(connections: List[Byte]*) extends ChannelFactory {
    private val pending = mutable.Queue(connections*)
    var opened = 0
    override def open(): ReadableWritableChannel = {
      opened += 1
      val bytes = pending.dequeue()
      new ReadableWritableChannel {
        private var unread = bytes
        override def read(buffer: ByteBuffer, timeoutMs: Int): Int = {
          val n = unread.size
          buffer.put(unread.toArray)
          unread = Nil
          n
        }
        override def write(buffer: ByteBuffer): Unit = buffer.position(buffer.limit())
        override def close(): Unit = {}
      }
    }
  }

  private def driver(factory: ChannelFactory) =
    new DefaultTestDriver(
      StubDriver.reporter(),
      factory,
      UInt16LE(1),
      UInt16LE(1024),
      1000,
      1000,
      1000,
      500,
      new VirtualClock)

  test("a reconnected DUT starts its FCB at 1 again") {
    val factory = new Factory(response(fcb = true), response(fcb = true))
    val dut = driver(factory)
    dut.readMessage(Unsolicited.No) should not be empty
    dut.close()
    dut.readMessage(Unsolicited.No) should not be empty
    factory.opened should equal(2)
  }

  test("on one connection the FCB alternates") {
    val dut = driver(new Factory(response(fcb = true) ::: response(fcb = true)))
    dut.readMessage(Unsolicited.No) should not be empty
    an[Exception] should be thrownBy dut.readMessage(Unsolicited.No)
  }
}
