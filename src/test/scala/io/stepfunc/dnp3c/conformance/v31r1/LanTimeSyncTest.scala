// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import scala.collection.mutable
import scala.util.Try

import io.stepfunc.dnp3c.conformance.api.Options
import io.stepfunc.dnp3c.conformance.api.options.TimeSyncOptions
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.{ AppFunction, GenIndexedPoint, IIN, MeasurementPoint, Message, Unsolicited }
import io.stepfunc.dnp3c.dnp3.app.objects.Group50Var3
import io.stepfunc.dnp3c.dnp3.link.{ FrameValidator, LinkFrame }
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// 3.1r1's 8.7.2: a Record Current Time broadcast, then a write of the time it was sent
@RunWith(classOf[JUnitRunner])
class LanTimeSyncTest extends AnyFunSuite with Matchers {

  private val plugin = new DefaultIntegrationPlugin {
    override def cyclePower(reporter: PluginReporter): Unit = ()
    override def generateTimeEvent(reporter: PluginReporter): Unit = ()
    override def verifyTimestamp(
        reporter: CheckReporter,
        points: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck] = None
  }

  /**
   * Needs the time until a g50v3 write, which it answers with IIN1.0, if it saw a broadcast Record Current Time; reports
   * a time-tagged binary input event when read
   */
  private class Dut(answersBroadcast: Boolean) extends StubDriver(StubDriver.reporter(plugin)) {
    private var needTime = true
    private var recorded = false
    private val responses = mutable.Queue.empty[Message]
    val written = mutable.ListBuffer.empty[Message]
    var broadcastAt: Option[Long] = None

    private def respond(seq: Int, iin: IIN, objects: String = "", con: Boolean = false): Unit =
      responses.enqueue(Message.parse(Hex.from(
        "%02X 81 %02X %02X %s".format(0xC0 | (if (con) 0x20 else 0) | seq, iin.lsb & 0xFF, iin.msb & 0xFF, objects))))

    override def writeMessage(message: Message, options: LinkOverride): Unit = {
      val seq = message.ctrl.seq.value.toInt
      val needTimeIin = if (needTime) IIN.NEED_TIME else IIN.empty
      message.function match {
        case AppFunction.recordCurrentTime =>
          broadcastAt = Some(System.currentTimeMillis())
          recorded = options.destination.exists(_.value == 0xFFFF)
          if (answersBroadcast) respond(seq, needTimeIin)
        case AppFunction.write if message.headers.exists(_.variation == Group50Var3) =>
          written += message
          if (recorded) needTime = false
          respond(seq, (if (needTime) IIN.NEED_TIME else IIN.empty) | IIN.BROADCAST)
        case AppFunction.read if message.headers.size == 3 =>
          // g2v2 at index 0, flags 0x01, time 1
          respond(seq, needTimeIin, "02 02 17 01 00 01 01 00 00 00 00 00", con = true)
        case AppFunction.read => respond(seq, needTimeIin)
        case _ =>
      }
    }

    override def readMessage(
        unsolicited: Unsolicited,
        validator: FrameValidator,
        linkOverride: LinkOverride,
        timeoutMs: Option[Int]): Option[Message] =
      if (responses.isEmpty) timedOut(timeoutMs) else Some(responses.dequeue())

    override def readFrame(timeout: Option[Int]): Option[LinkFrame] =
      if (responses.isEmpty) timedOut(timeout) else throw new Exception("a response to the broadcast")
    override def close(): Unit = {}
  }

  private val options = Options.from(Map(TimeSyncOptions.supportLanTimeSync.id -> "true"))

  private def run(dut: Dut): Boolean =
    Try(LanTimeSync.lanTimeSync.configure(options).steps.foreach(s => s.step.run(dut).foreach(_.check()))).isSuccess

  test("the DUT takes the time it recorded at the broadcast, and reports IIN1.0") {
    val dut = new Dut(answersBroadcast = false)
    run(dut) should equal(true)
    // the write carries the time the broadcast went out, to within the time the stub took to see it
    val time = dut.written.head.toBytes.takeRight(6).zipWithIndex.map((b, i) => (b & 0xFFL) << (8 * i)).sum
    math.abs(time - dut.broadcastAt.get) should be < 1000L
  }

  test("an answer to the broadcast fails") {
    run(new Dut(answersBroadcast = true)) should equal(false)
  }

  test("it runs only where the Device Profile claims the LAN method") {
    LanTimeSync.lanTimeSync.disabledBy(Options.from(Map())) should not equal None
    LanTimeSync.lanTimeSync.disabledBy(options) should equal(None)
  }
}
