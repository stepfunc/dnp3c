// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.{ Revisions, v27 }
import io.stepfunc.dnp3c.conformance.api.{ Options, ProcedureDefinition, TestProcedure }
import io.stepfunc.dnp3c.conformance.api.options.{ ClassOptions, OutstationOptions }
import io.stepfunc.dnp3c.dnp3.{ LinkOverride, Policies, StubDriver }
import io.stepfunc.dnp3c.dnp3.app.{ Message, Unsolicited }
import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// 3.1r1's 8.5 tests: 2.7's steps, with 3.1r1's numbering and checks, and the tables at the DUT's subset level
@RunWith(classOf[JUnitRunner])
class ClassDataTest extends AnyFunSuite with Matchers {

  private def configure(procedure: ProcedureDefinition, level: Int = 2) =
    procedure.configure(Options.from(Map(OutstationOptions.subsetLevel.id -> level.toString))).steps.toList

  private def lastStep(procedure: ProcedureDefinition): String = configure(procedure).last.label.stripPrefix("step: ")

  private def describes(procedure: ProcedureDefinition, level: Int = 2): String =
    configure(procedure, level).flatMap(_.step.description).mkString("\n")

  test("the confirmations 3.1r1 numbers move the later steps on") {
    lastStep(v27.Procedure8_5_2_2_1) should equal("14")
    lastStep(ClassData.class1All) should equal("16")
    lastStep(v27.Procedure8_5_2_2_2) should equal("15")
    // 8.5.2.2.2 numbers only the second confirmation
    lastStep(ClassData.class1OneByteCount) should equal("16")
    lastStep(ClassData.class2OneByteCount) should equal("17")
    lastStep(ClassData.class3TwoByteCount) should equal("17")
    lastStep(v27.Procedure8_5_6_2) should equal("26")
    lastStep(ClassData.classAssignment) should equal("30")
  }

  test("the 0x06 tests check that the class's IIN1 bit is clear once its events are reported, which 2.7's don't") {
    describes(ClassData.class2All) should include("Class2Events")
    describes(v27.Procedure8_5_3_2_1) should not include "Class2Events"
  }

  test("the tables apply at the DUT's subset level") {
    describes(ClassData.class1All, level = 3) should include("table 8-4, for subset level 3")
    describes(v27.Procedure8_5_1_2, level = 1) should include("table 8-3, for subset level 1")
  }

  test("the tests whose steps are 2.7's share its definitions") {
    def definition(id: String) = Revisions.v3_1r1.elements(id).asInstanceOf[TestProcedure].definition
    definition("8.5.1-1") should be theSameInstanceAs v27.Procedure8_5_1_2
    definition("8.5.3.2.4") should be theSameInstanceAs v27.Procedure8_5_3_2_4
    definition("8.5.5.2.2") should be theSameInstanceAs v27.Procedure8_5_5_2_2
  }

  test("a Null Response to a class read may ask for confirmation, as 8.5.2 to 8.5.4 allow, despite Note 7") {
    // answers every request with a Null Response that asks for confirmation, checking it against 3.1r1's rules as
    // the harness's driver does
    val dut = new StubDriver(StubDriver.reporter()) {
      override val policies: Policies = new Policies(Revisions.v3_1r1.policies)
      private var seq = 0
      override def writeMessage(message: Message, options: LinkOverride): Unit = seq = message.ctrl.seq.value.toInt
      override def readMessage(
          unsolicited: Unsolicited,
          validator: FrameValidator,
          linkOverride: LinkOverride,
          timeoutMs: Option[Int]): Option[Message] = {
        val response = Message.parse(Hex.from("%02X 81 00 00".format(0xE0 | seq)))
        policies.check(response)
        Some(response)
      }
    }
    val options = Options.from(Map(OutstationOptions.subsetLevel.id -> "2", ClassOptions.supportClass1.id -> "false"))
    Run.passing(ClassData.class1All, dut, options)
    a[Exception] should be thrownBy dut.readMessage(Unsolicited.No, FrameValidator.none, LinkOverride.default, None)
  }
}
