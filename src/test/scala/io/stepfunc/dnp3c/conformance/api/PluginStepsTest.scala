// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api

import io.stepfunc.dnp3c.dnp3.{ ActionResult, ActionTiming, DefaultIntegrationPlugin, PluginReporter, StubDriver }
import io.stepfunc.dnp3c.dnp3.app.EventClass
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class PluginStepsTest extends AnyFunSuite with Matchers {

  private class Plugin extends DefaultIntegrationPlugin {
    val calls = mutable.ListBuffer.empty[String]
    val timings = mutable.ListBuffer.empty[ActionTiming]
    override def uninstallBinaryOutputs(reporter: PluginReporter): Unit = calls += "uninstall"
    override def generateClassEvents(reporter: PluginReporter, eventClass: EventClass): Unit =
      calls += eventClass.toString
    override def generateUnsolicitedEvents(reporter: PluginReporter, timing: ActionTiming): ActionResult = {
      timings += timing
      ActionResult.Performed
    }
  }

  private class Driver(val plugin: Plugin) extends StubDriver(StubDriver.reporter(plugin))

  test("an action has the plugin of the driver that runs it act, and defers nothing") {
    val step = PluginSteps.uninstallBinaryOutputs
    val (first, second) = (new Driver(new Plugin), new Driver(new Plugin))

    step.run(second) should equal(Nil)

    first.plugin.calls should equal(Nil)
    second.plugin.calls should equal(List("uninstall"))
  }

  test("events of several classes are generated in the order given, and none for no classes") {
    val driver = new Driver(new Plugin)
    PluginSteps.generateClassEvents(List(EventClass.Class1, EventClass.Class3)).run(driver)
    PluginSteps.generateClassEvents(Nil).run(driver)
    driver.plugin.calls should equal(List(EventClass.Class1.toString, EventClass.Class3.toString))
  }

  test("an untimed step has the plugin generate unsolicited events now") {
    val driver = new Driver(new Plugin)
    PluginSteps.generateUnsolicitedEvents().run(driver)
    driver.plugin.timings.toList should equal(List(ActionTiming.Now))
  }
}
