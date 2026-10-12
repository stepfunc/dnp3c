// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s7

import io.stepfunc.dnp3c.conformance.api.Options
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.{
  AppFunction, Confirm, Final, First, IIN, Message, Sequence, Unsolicited, Control => AppControl
}
import io.stepfunc.dnp3c.dnp3.link.{ Control, FrameValidator, LinkFrame, LinkFunction }
import io.stepfunc.dnp3c.dnp3.transport.Transport
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.util.Try

@RunWith(classOf[JUnitRunner])
class Procedure72Test extends AnyFunSuite with Matchers {

  // answers the class 0 read with `fragments`, each sent as transport segments carrying the given numbers of octets
  private class Dut(fragments: List[List[Int]]) extends StubDriver(StubDriver.reporter(new DefaultIntegrationPlugin {
        override def cyclePower(reporter: PluginReporter): Unit = ()
      })) {
    private var pending = fragments
    private var segmentSeq = 0
    override def writeMessage(message: Message, options: LinkOverride): Unit = ()
    override def readMessage(
        unsolicited: Unsolicited,
        validator: FrameValidator,
        linkOverride: LinkOverride,
        timeoutMs: Option[Int]): Option[Message] = pending match {
      case Nil => timedOut(timeoutMs)
      case sizes :: rest =>
        sizes.zipWithIndex.foreach { (size, i) =>
          val header = Transport.function(i == 0, i == sizes.size - 1, Transport.rotate(segmentSeq))
          val control = Control(false, segmentSeq % 2 == 0, true, LinkFunction.CONFIRMED_USER_DATA)
          validator.validate(LinkFrame.withData(
            control,
            UInt16LE(1),
            UInt16LE(1024),
            header :: List.fill(size)(0.toByte)))
          segmentSeq += 1
        }
        val seq = Sequence((fragments.size - pending.size).toByte)
        pending = rest
        val ctrl = AppControl(First(seq.value == 0), Final(rest.isEmpty), Confirm.No, Unsolicited.No, seq)
        Some(Message(ctrl, AppFunction.response, Some(IIN.empty), Nil))
    }
    override def close(): Unit = ()
  }

  private def passes(fragments: List[Int]*): Boolean = {
    val dut = new Dut(fragments.toList)
    Try(Procedure72.configure(Options.from(Map())).steps.foreach(_.step.run(dut))).isSuccess
  }

  test("a reply that fits one link frame must be one segment") {
    passes(List(249)) should equal(true)
    passes(List(100, 100)) should equal(false)
    passes(List(200, 49)) should equal(false)
  }

  test("a reply too long for one link frame may be several segments") {
    passes(List(249, 1)) should equal(true)
    passes(List(249, 249, 10)) should equal(true)
  }

  test("each fragment of the reply is checked on its own") {
    passes(List(100), List(100)) should equal(true)
    passes(List(249, 249), List(100, 100)) should equal(false)
  }
}
