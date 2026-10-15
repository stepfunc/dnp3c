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
import io.stepfunc.dnp3c.protocol.logging.{ Details, Level, Logger }
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// 3.1r1's 8.26.2, with binary input 0 in class 1 and 1 and 2 in class 2 at startup
@RunWith(classOf[JUnitRunner])
class AssignClassTest extends AnyFunSuite with Matchers {

  private val startup = Map(0 -> 1, 1 -> 2, 2 -> 2)

  /**
   * Keeps each binary input's class, which Assign Class changes unless the DUT ignores it, doesn't implement it, or
   * rejects it, as it does each one after the first `accepts`, and its events, which a read of their class reports and a
   * confirmation clears; if split, it answers the first Assign Class request in two fragments
   */
  private class Dut(
      ignoresAssign: Boolean = false,
      notImplemented: Boolean = false,
      accepts: Int = Int.MaxValue,
      split: Boolean = false)
      extends RespondingDriver {
    val classes: mutable.Map[Int, Int] = mutable.Map.from(startup)
    private var events = List.empty[(Int, Int)] // point, class
    private var awaiting: Option[(Int, List[(Int, Int)])] = None
    private var assigned = 0

    // the errors the run reports
    val errors: mutable.ListBuffer[String] = mutable.ListBuffer.empty
    override lazy val reporter: ProcedureReporter = new ProcedureReporter(
      new Logger {
        def info(lines: String): Unit = ()
        def warn(lines: String): Unit = ()
        def error(lines: String): Unit = errors += lines
        def note(message: String, details: Iterable[Details]): Unit = ()
        def plugin(message: String): Unit = ()
        def transmit(level: Level, message: String, details: Iterable[Details]): Unit = ()
        def receive(level: Level, message: String, details: Iterable[Details]): Unit = ()
      },
      testPlugin
    )

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
          assigned += 1
          if (notImplemented) enqueue("%02X 81 00 01".format(0xC0 | seq))
          else if (assigned > accepts) enqueue("%02X 81 00 04".format(0xC0 | seq)) // IIN2.2
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
            if (split && assigned == 1) {
              enqueue("%02X 81 %02X 00".format(0x80 | seq, classBits(events)))
              enqueue("%02X 81 %02X 00".format(0x40 | (seq + 1), classBits(events)))
            } else enqueue("%02X 81 %02X 00".format(0xC0 | seq, classBits(events)))
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
      ClassOptions.class1.binaryInputs.id -> "0",
      ClassOptions.class2.binaryInputs.id -> "1, 2"))
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

  test("the cleanup reports a DUT that rejects having its classes put back") {
    val dut = new Dut(accepts = 1)
    run(dut)._1 should equal(false)
    dut.classes.toMap should not equal startup
    dut.errors.exists(_.startsWith("Cleanup failed")) should equal(true)
  }

  test("a level 1 or 2 DUT may answer Assign Class with IIN2.0, which is noted; a level 3 one may not") {
    val (passed, notes) = run(new Dut(notImplemented = true), level = 2)
    passed should equal(true)
    notes.exists(_.contains("IIN2.0")) should equal(true)
    run(new Dut(notImplemented = true), level = 3)._1 should equal(false)
  }

  test("an answer to Assign Class in two fragments fails, at level 2 as at level 3") {
    run(new Dut(split = true), level = 3)._1 should equal(false)
    run(new Dut(split = true), level = 2)._1 should equal(false)
  }

  test("8.26.2 needs a class 1 binary input and two class 2 ones, and 8.26.3 two in each class") {
    val classes = Map(
      ClassOptions.class1.supported.id -> "true",
      ClassOptions.class2.supported.id -> "true",
      ClassOptions.class3.supported.id -> "true")
    def problems(test: io.stepfunc.dnp3c.conformance.api.ProcedureDefinition, points: (String, String)*) =
      test.configurationProblems(Options.from(classes ++ points)).map(_.key)
    val class1 = ClassOptions.class1.binaryInputs.id
    val class2 = ClassOptions.class2.binaryInputs.id
    val class3 = ClassOptions.class3.binaryInputs.id
    problems(AssignClass.assignClass, class1 -> "0", class2 -> "1, 2") should equal(Nil)
    problems(AssignClass.assignClass, class1 -> "0", class2 -> "1") should equal(List(class2))
    problems(AssignClass.noClass, class1 -> "0, 1", class2 -> "2, 3", class3 -> "4, 5") should equal(Nil)
    problems(AssignClass.noClass, class1 -> "0", class2 -> "2, 3", class3 -> "4, 5") should equal(List(class1))
  }
}
