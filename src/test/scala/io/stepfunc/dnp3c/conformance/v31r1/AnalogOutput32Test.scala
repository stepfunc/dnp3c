// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.api.options.AnalogOutputOptions
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.{ AppFunction, IIN, Message, ObjectHeader }
import io.stepfunc.dnp3c.dnp3.app.objects.CommandStatus
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// 3.1r1's 8.4.1.2.13: a select with a 32-bit value, which the DUT rejects, finds out of range, or carries out
@RunWith(classOf[JUnitRunner])
class AnalogOutput32Test extends AnyFunSuite with Matchers {

  private class Plugin extends DefaultIntegrationPlugin {
    var operated: List[Int] = Nil
    override def checkOperate(reporter: CheckReporter, kind: OutputKind, index: Int): Option[DeferredCheck] = {
      require(kind == PointKind.AnalogOutputStatus, s"checked a ${kind.name}")
      operated :+= index
      None
    }
  }

  // runs the test against a DUT that answers each command request as given, and returns whether it passed and the
  // outputs the plugin was asked to check
  private def run(settings: Map[String, String], answer: Message => (IIN, List[ObjectHeader])): (Boolean, List[Int]) = {
    val plugin = new Plugin
    val dut = new ScriptedDut(StubDriver.reporter(plugin), answer)
    val passed =
      Run.passes(AnalogOutput32.sbo, dut, settings)
    (passed, plugin.operated)
  }

  private val echo: Message => (IIN, List[ObjectHeader]) = m => (IIN.empty, m.headers)

  test("a DUT without 32-bit commands answers with IIN2.1") {
    run(Map(), _ => (IIN.OBJECT_UNKNOWN, Nil)) should equal((true, Nil))
    run(Map(), echo)._1 should equal(false)
  }

  test("a DUT with 32-bit commands but no 32-bit point answers with status 12, out of range") {
    val settings = Map(AnalogOutputOptions.commands32Bit.id -> "true")
    val outOfRange = (m: Message) => (IIN.empty, List(AnalogOutput32.select(0, CommandStatus.OUT_OF_RANGE)))
    run(settings, outOfRange) should equal((true, Nil))
    run(settings, echo)._1 should equal(false)
  }

  test("a DUT with a 32-bit point echoes the select and the execute, and the point operates") {
    val settings = Map(AnalogOutputOptions.commands32Bit.id -> "true", AnalogOutputOptions.index32Bit.id -> "5")
    run(settings, echo) should equal((true, List(5)))
    run(settings, m => if (m.function == AppFunction.operate) (IIN.empty, Nil) else echo(m))._1 should equal(false)
  }
}
