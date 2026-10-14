// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import scala.collection.mutable

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.api.ProcedureDefinition
import io.stepfunc.dnp3c.conformance.api.options.ControlOptions
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Outputs
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.Group60
import io.stepfunc.dnp3c.dnp3.link.LinkFrame
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// 3.1r1's 10.3.3 and 10.3.4, with binary outputs 0 and 1
@RunWith(classOf[JUnitRunner])
class OutputEventsTest extends AnyFunSuite with Matchers {

  /**
   * Binary outputs 0 and 1, flags 0x01, state off; a flag change makes a g11 event; a select arms its point, and an
   * operate of the armed point succeeds, of another fails with NO_SELECT (2); each makes a g13 event with its status,
   * unless the DUT reports a wrong status. Class polls report the events, which a confirmation clears.
   */
  private class Dut(wrongStatus: Boolean = false, hidesFlag: Boolean = false)
      extends RespondingDriver {
    private val flags = mutable.Map(0 -> 0x01, 1 -> 0x01)
    private var events = List.empty[String]
    private var awaiting: Option[Int] = None
    private var selected: Option[Int] = None

    override protected val testPlugin: DefaultIntegrationPlugin = new DefaultIntegrationPlugin {
      override def changeOutputFlag(reporter: PluginReporter, kind: PointKind, index: Int, flag: Int): Unit = {
        flags(index) ^= 1 << flag
        if (!hidesFlag) events :+= "0B 01 17 01 %02X %02X".format(index, flags(index))
      }
      override def verifyCommandEventTime(reporter: CheckReporter, sentMs: Long, eventMs: Long): Option[DeferredCheck] =
        None
    }

    override def writeMessage(message: Message, options: LinkOverride): Unit = {
      val seq = message.ctrl.seq.value.toInt
      def respond(con: Boolean, iin: String, objects: String): Unit =
        enqueue("%02X 81 %s %s".format(
          0xC0 | (if (con) 0x20 else 0) | seq,
          iin,
          objects))
      message.function match {
        case AppFunction.confirm =>
          if (awaiting.contains(seq)) events = Nil
          awaiting = None
        case AppFunction.select =>
          val bytes = message.headers.head.toBytes
          selected = Some(bytes(5) & 0xFF)
          respond(con = false, "00 00", Hex.convert(bytes))
        case AppFunction.operate =>
          val bytes = message.headers.head.toBytes
          val index = bytes(5) & 0xFF
          val status = if (selected.contains(index)) 0 else 2
          selected = None
          events :+= "0D 01 17 01 %02X %02X".format(index, if (wrongStatus) 4 else status)
          respond(con = false, "00 00", Hex.convert(bytes.init :+ status.toByte))
        case _ =>
          val class0 = message.headers.exists(_.variation.id == 1) &&
            message.headers.forall(_.variation.group == Group60)
          val statics = if (class0 && message.headers.size == 4)
            " 0A 02 00 00 01 %02X %02X".format(flags(0), flags(1))
          else ""
          if (events.isEmpty) respond(con = false, "00 00", statics.trim)
          else {
            respond(con = true, "00 00", (events.mkString(" ") + statics).trim)
            awaiting = Some(seq)
          }
      }
    }

    override def readFrame(timeout: Option[Int]): Option[LinkFrame] = timedOut(timeout)
  }

  private def run(procedure: ProcedureDefinition, dut: Dut, settings: Map[String, String] = Map()): Boolean =
    Run.passes(procedure, dut, settings)

  private val commandEvents = Map(Outputs.binary.commandEvents.id -> "true")

  test("10.3.4: a command event for each command, with the status the operate got") {
    run(OutputEvents.binaryCommandEvents, new Dut, commandEvents) should equal(true)
    run(OutputEvents.binaryCommandEvents, new Dut(wrongStatus = true), commandEvents) should equal(false)
  }

  test("10.3.3: an event for each flag changed, with the flag changed and nothing else") {
    val settings = Map(Outputs.binary.events.id -> "true", ControlOptions.statusFlags.id -> "1, 2")
    run(OutputEvents.binaryStatusEvents, new Dut, settings) should equal(true)
    run(OutputEvents.binaryStatusEvents, new Dut(hidesFlag = true), settings) should equal(false)
  }
}
