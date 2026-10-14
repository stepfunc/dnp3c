// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import java.io.StringReader

@RunWith(classOf[JUnitRunner])
class Procedure8_17_2_2Test extends AnyFunSuite with Matchers {

  // records the actions it's asked for, in order
  private class Plugin extends DefaultIntegrationPlugin {
    val actions = scala.collection.mutable.ListBuffer.empty[String]
    override def recordCurrentCounterValues(reporter: PluginReporter): Unit = actions += "record"
    override def generateInputChanges(reporter: PluginReporter, kind: InputKind[?]): Unit = actions += "generate"
  }

  test("the counter values are recorded before more counts are generated") {
    val plugin = new Plugin
    val dut = new ScriptedDut(StubDriver.reporter(plugin), _ => (IIN.empty, Nil))
    Console.withIn(new StringReader(""))(Run.everyStep(Procedure8_17_2_2, dut))
    plugin.actions.toList should equal(List("generate", "record", "generate"))
  }
}
