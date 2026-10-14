// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s87

import io.stepfunc.dnp3c.conformance.api.options.OutstationOptions
import io.stepfunc.dnp3c.conformance.api.Options
import io.stepfunc.dnp3c.conformance.s8.s87.s872.Procedure8722
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable
import scala.util.Try

@RunWith(classOf[JUnitRunner])
class Procedure8722Test extends AnyFunSuite with Matchers {

  private class Plugin extends DefaultIntegrationPlugin {
    var timestampChecks = List.empty[Int]
    override def cyclePower(reporter: PluginReporter): Unit = ()
    override def generateTimeEvent(reporter: PluginReporter): Unit = ()
    override def verifyTimestamp(
        reporter: CheckReporter,
        points: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck] = {
      timestampChecks = timestampChecks :+ points.size
      None
    }
  }

  // needs time until it's written; answers the class 1, 2 and 3 read with `fragments`, each the objects of one
  private class Dut(plugin: Plugin, fragments: List[String]) extends StubDriver(StubDriver.reporter(plugin)) {
    private var needTime = true
    private val pending = mutable.Queue.empty[Message]
    override def close(): Unit = ()

    private def respond(ctrl: Int, objects: String): Unit = {
      val iin = if (needTime) "10 00" else "00 00"
      pending.enqueue(Message.parse(Hex.from("%02X 81 %s %s".format(ctrl, iin, objects))))
    }

    override def writeMessage(message: Message, options: LinkOverride): Unit = {
      val seq = message.ctrl.seq.value
      message.function match {
        case AppFunction.write =>
          needTime = false
          respond(0xC0 | seq, "")
        case AppFunction.read if seq == 2 =>
          fragments.zipWithIndex.foreach { (objects, i) =>
            val fir = if (i == 0) 0x80 else 0
            val fin = if (i == fragments.size - 1) 0x40 else 0
            respond(fir | fin | 0x20 | ((seq + i) & 0x0F), objects)
          }
        case AppFunction.read => respond(0xC0 | seq, "")
        case _ =>
      }
    }

    override def readMessage(
        unsolicited: Unsolicited,
        validator: FrameValidator,
        linkOverride: LinkOverride,
        timeoutMs: Option[Int]): Option[Message] = if (pending.isEmpty) timedOut(timeoutMs) else Some(pending.dequeue())
  }

  private val timedBinary = "02 02 17 01 00 81 00 00 00 00 00 00" // g2v2: binary input 0, with time
  private val binary = "02 01 17 01 00 81" // g2v1: binary input 0, without time
  private val analog = "20 01 17 01 05 01 00 00 00 00" // g32v1: analog input 5, without time

  private def passes(plugin: Plugin, fragments: String*): Boolean = {
    val dut = new Dut(plugin, fragments.toList)
    Try(Procedure8722.configure(
      Options.from(Map(OutstationOptions.subsetLevel.id -> "2"))).steps.foreach(_.step.run(dut))).isSuccess
  }

  test("accepts a time-tagged event") {
    passes(new Plugin, timedBinary) should equal(true)
  }

  test("rejects a response whose events carry no time") {
    passes(new Plugin, binary) should equal(false)
    passes(new Plugin, analog + " " + binary) should equal(false)
  }

  test("rejects static objects") {
    passes(new Plugin, timedBinary + " 01 02 00 00 00 81") should equal(false)
  }

  test("accepts several fragments, and gives the plugin the events of all of them at once") {
    val plugin = new Plugin
    passes(plugin, analog, timedBinary) should equal(true)
    plugin.timestampChecks should equal(List(2))
  }
}
