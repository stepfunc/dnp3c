// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import java.io.StringReader

import scala.collection.mutable

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.api.{ Options, ProcedureDefinition }
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.{ IIN, Message, ObjectHeader }
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class LevelOneAndDisableTest extends AnyFunSuite with Matchers {

  private class Plugin extends DefaultIntegrationPlugin {
    val calls = mutable.ListBuffer.empty[String]
    override def uninstall(reporter: PluginReporter, kind: PointKind): Unit = calls += "uninstall"
    override def reinstallPoints(reporter: PluginReporter): Unit = calls += "reinstall"
  }

  // answers every read with nothing but the IIN bits
  private def run(procedure: ProcedureDefinition, options: Options, iin: IIN, plugin: Plugin = new Plugin) = {
    val reporter = StubDriver.reporter(plugin)
    val dut = new ScriptedDut(reporter, (_: Message) => (iin, List.empty[ObjectHeader]))
    val passed = Console.withIn(new StringReader(""))(Run.passes(procedure, dut, options))
    (passed, reporter.notes, plugin.calls.toList)
  }

  private val levelOne = Options.from(Map(Inputs.binary.eventReads.id -> "false"))

  test("8.14.2a: a level 1 device that can't read events answers with an error IIN, which the result notes") {
    val (passed, notes, _) = run(InputEvents.binaryLevelOne, levelOne, IIN.OBJECT_UNKNOWN)
    passed should equal(true)
    notes.head should include("ObjectUnknown")
    run(InputEvents.binaryLevelOne, levelOne, IIN.FUNC_NOT_SUPPORTED)._1 should equal(true)
    run(InputEvents.binaryLevelOne, levelOne, IIN.empty)._1 should equal(false)
  }

  test("8.14.2a runs only for a device that can't read events") {
    InputEvents.binaryLevelOne.disabledBy(Options.from(Map())) should not equal None
    InputEvents.binaryLevelOne.disabledBy(levelOne) should equal(None)
  }

  test("8.22.1 disables the double-bit inputs, reads g4, and restores them") {
    val (passed, _, calls) = run(DisableDoubleBit.events, Options.from(Map()), IIN.empty)
    passed should equal(true)
    calls should equal(List("uninstall", "reinstall"))
    run(DisableDoubleBit.events, Options.from(Map()), IIN.OBJECT_UNKNOWN)._1 should equal(true)
  }

  test("8.21.1 needs an error response; and a device that can't disable its double-bit inputs fails") {
    run(DisableDoubleBit.static, Options.from(Map()), IIN.empty)._1 should equal(false)
    run(DisableDoubleBit.static, Options.from(Map()), IIN.OBJECT_UNKNOWN)._1 should equal(true)
    run(DisableDoubleBit.events, Options.from(Map(Inputs.doubleBit.disabling.id -> "false")), IIN.empty)._1 should
      equal(false)
  }
}
