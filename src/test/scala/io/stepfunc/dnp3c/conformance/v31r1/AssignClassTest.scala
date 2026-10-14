// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import scala.collection.mutable

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.api.Options
import io.stepfunc.dnp3c.conformance.api.options.{ ClassOptions, OutstationOptions }
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects._
import io.stepfunc.dnp3c.dnp3.link.LinkFrame
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// 3.1r1's 8.26.2, with binary input 0 in class 1 and 1 and 2 in class 2 at startup
@RunWith(classOf[JUnitRunner])
class AssignClassTest extends AnyFunSuite with Matchers {

  private val startup = Map(0 -> 1, 1 -> 2, 2 -> 2)

  /**
   * Keeps each binary input's class, which Assign Class changes unless the DUT ignores it or doesn't implement it, and
   * its events, which a read of their class reports and a confirmation clears
   */
  private class Dut(ignoresAssign: Boolean = false, notImplemented: Boolean = false)
      extends RespondingDriver {
    val classes: mutable.Map[Int, Int] = mutable.Map.from(startup)
    private var events = List.empty[(Int, Int)] // point, class
    private var awaiting: Option[(Int, List[(Int, Int)])] = None

    override protected val testPlugin: DefaultIntegrationPlugin = new DefaultIntegrationPlugin {
      override def generateEventOn(reporter: PluginReporter, kind: PointKind, index: Int): Unit =
        if (classes(index) != 0) events :+= (index, classes(index))
      override def verifyAllStaticCurrentValues(
          reporter: CheckReporter,
          isClass0: Boolean,
          points: Seq[GenIndexedPoint[MeasurementPoint]]): Option[DeferredCheck] = None
    }

    private def classBits(remaining: List[(Int, Int)]): Int =
      remaining.map(_._2).distinct.map(c => 1 << c).sum

    override def writeMessage(message: Message, options: LinkOverride): Unit = {
      val seq = message.ctrl.seq.value.toInt
      message.function match {
        case AppFunction.confirm =>
          awaiting.filter(_._1 == seq).foreach((_, sent) => events = events.filterNot(sent.contains))
          awaiting = None
        case AppFunction.assignClass =>
          if (notImplemented) enqueue("%02X 81 00 01".format(0xC0 | seq))
          else {
            if (!ignoresAssign) {
              var current = 0
              message.headers.foreach { h =>
                if (h.variation.group == Group60) current = h.variation.id.toInt - 1
                else h match {
                  case r: OneByteStartStopHeader => classes(r.start.value) = current
                  case _ =>
                }
              }
            }
            enqueue("%02X 81 %02X 00".format(0xC0 | seq, classBits(events)))
          }
        case _ =>
          val requested = message.headers.map(_.variation).collect {
            case Group60Var2 => 1
            case Group60Var3 => 2
            case Group60Var4 => 3
          }.toSet
          val sent = events.filter(e => requested.contains(e._2))
          val objects =
            if (sent.isEmpty) ""
            else "02 01 17 %02X %s".format(sent.size, sent.map(e => "%02X 01".format(e._1)).mkString(" "))
          val con = if (sent.nonEmpty) 0x20 else 0
          enqueue("%02X 81 %02X 00 %s".format(0xC0 | con | seq, classBits(events.filterNot(sent.contains)), objects))
          if (sent.nonEmpty) awaiting = Some((seq, sent))
      }
    }

    override def readFrame(timeout: Option[Int]): Option[LinkFrame] = timedOut(timeout)
  }

  private def run(dut: Dut, level: Int = 3): (Boolean, List[String]) = {
    val options = Options.from(Map(
      OutstationOptions.subsetLevel.id -> level.toString,
      ClassOptions.class1BinaryInputs.id -> "0",
      ClassOptions.class2BinaryInputs.id -> "1, 2"))
    val passed = Run.passes(AssignClass.assignClass, dut, options)
    (passed, dut.reporter.notes)
  }

  test("a DUT whose events follow the classes assigned passes, and has its classes back at the end") {
    val dut = new Dut
    run(dut)._1 should equal(true)
    dut.classes.toMap should equal(startup)
  }

  test("a DUT that ignores Assign Class fails, and gets its classes back anyway") {
    val dut = new Dut(ignoresAssign = true)
    run(dut)._1 should equal(false)
    dut.classes.toMap should equal(startup)
  }

  test("a level 1 or 2 DUT may answer Assign Class with IIN2.0, which is noted; a level 3 one may not") {
    val (passed, notes) = run(new Dut(notImplemented = true), level = 2)
    passed should equal(true)
    notes.exists(_.contains("IIN2.0")) should equal(true)
    run(new Dut(notImplemented = true), level = 3)._1 should equal(false)
  }
}
