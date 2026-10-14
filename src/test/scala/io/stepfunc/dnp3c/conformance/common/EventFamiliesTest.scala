// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.common

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.Placements.id
import io.stepfunc.dnp3c.conformance.api.ProcedureDefinition
import io.stepfunc.dnp3c.conformance.api.options.BinaryInputOptions
import io.stepfunc.dnp3c.conformance.v27.Procedure8_6_6_2_1
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group2Var0, Group60Var3 }
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class EventFamiliesTest extends AnyFunSuite with Matchers {

  private val reporter = StubDriver.reporter()

  private def passes(validation: MessageValidation, objects: String): Boolean =
    scala.util.Try(validation.validate(reporter, Message.parse(Hex.from("C0 81 00 00 " + objects)))).isSuccess

  test("a counted read uses qualifier 0x07, or 0x08 for a two-byte count") {
    Requests.readCount(Sequence.zero, Group2Var0, 3, twoByte = false).toBytes should equal(
      Hex.from("C0 01 02 00 07 03"))
    Requests.readCount(Sequence.zero, Group2Var0, 3, twoByte = true).toBytes should equal(
      Hex.from("C0 01 02 00 08 03 00"))
  }

  test("relative-time events must each follow a CTO, and nothing else may be in the response") {
    val v = new InputEventSteps(InputType.BinaryInput).verifyVar3WithCtoResponse
    val cto = "33 01 07 01 00 00 00 00 00 00"
    passes(v, s"$cto 02 03 17 01 05 81 00 00") should equal(true)
    passes(v, s"$cto 02 03 28 01 00 05 00 81 00 00") should equal(true)
    passes(v, "02 03 17 01 05 81 00 00") should equal(false) // no CTO
    passes(v, s"$cto 02 01 17 01 05 81") should equal(false) // another variation
    passes(v, "") should equal(false)
  }

  // answers each request by its sequence number with a whole response, given as bytes after the control and function
  private class Dut(answers: Map[Int, String]) extends RespondingDriver(StubDriver.reporter(new Plugin)) {
    val reads = mutable.ListBuffer.empty[(Int, List[Variation])]
    override def writeMessage(message: Message, options: LinkOverride): Unit =
      if (message.function == AppFunction.read) {
        val seq = message.ctrl.seq.value.toInt
        reads += ((seq, message.headers.map(_.variation)))
        enqueue(answers(seq))
      }
  }

  private class Plugin extends DefaultIntegrationPlugin {
    override def generateExactInputEvents(reporter: PluginReporter, kind: InputKind[?], numEvents: Int): Unit = ()
  }

  private val event = "02 01 17 01 05 81" // one g2v1 event
  // FIR FIN (CON) and the sequence number, the response function, then IIN1 and IIN2 (IIN2.3 is buffer overflow)
  private val overflowing = Map(
    0 -> "C0 81 00 00",
    1 -> s"C1 81 00 00 $event",
    2 -> s"E2 81 00 08 $event",
    3 -> s"E3 81 00 00 $event")

  private def run(
      procedure: ProcedureDefinition,
      answers: Map[Int, String],
      settings: Map[String, String] = Map()): Dut = {
    val dut = new Dut(answers)
    Run.passing(procedure, dut, settings)
    dut
  }

  test("an event buffer overflow is reported with the event after it, and cleared after that") {
    noException should be thrownBy run(Procedure8_6_6_2_1, overflowing)
    an[Exception] should be thrownBy run(Procedure8_6_6_2_1, overflowing.updated(2, s"E2 81 00 00 $event"))
    an[Exception] should be thrownBy run(Procedure8_6_6_2_1, overflowing.updated(3, s"E3 81 00 08 $event"))
    an[Exception] should be thrownBy run(Procedure8_6_6_2_1, overflowing.updated(1, s"C1 81 00 08 $event"))
  }

  test("the event buffer procedures read the class the points are assigned to") {
    val dut = run(Procedure8_6_6_2_1, overflowing, Map(BinaryInputOptions.eventsClass.id -> "2"))
    dut.reads.toList.drop(1).map(_._2) should equal(List.fill(3)(List(Group60Var3)))
  }
}
