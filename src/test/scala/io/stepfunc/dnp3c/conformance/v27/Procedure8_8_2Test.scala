// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.dnp3.{ DefaultIntegrationPlugin, PluginReporter, ScriptedDut, StubDriver, Validate }
import io.stepfunc.dnp3c.dnp3.app.{ AppFunction, IIN, Message }
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class Procedure8_8_2Test extends AnyFunSuite with Matchers {

  // the wait step 4 asks for after the restart response, or None if the response is rejected
  private def waitFor(objects: String): Option[Long] = {
    val restart = new Procedure8_8_2.RestartDelay
    val accepted = Validate.passes(restart.record, objects)
    Option.when(accepted) {
      val driver = new StubDriver(StubDriver.reporter()) {}
      restart.waitOut.run(driver)
      driver.clock.slept.sum
    }
  }

  test("step 4 waits out the reported delay, coarse (g52v1, seconds) or fine (g52v2, milliseconds), plus 1 s") {
    waitFor("34 01 07 01 02 00") should equal(Some(3000L))
    waitFor("34 02 07 01 F4 01") should equal(Some(1500L))
  }

  test("after the restart, the next request goes out on a new connection") {
    // answers the restart with a 500 ms delay, and anything else with a null response; counts the requests written
    // before each disconnect
    class Dut
        extends ScriptedDut(
          StubDriver.reporter(),
          request =>
            if (request.function == AppFunction.coldRestart)
              (IIN.empty, Message.parse(Hex.from("C0 81 00 00 34 02 07 01 F4 01")).headers)
            else (IIN.DEVICE_RESTART, Nil)
        ) {
      val closedAfter = scala.collection.mutable.ListBuffer.empty[Int]
      override def close(): Unit = closedAfter += requests.count(_.function != AppFunction.confirm)
    }
    val dut = new Dut
    Run.everyStep(Procedure8_8_2, dut)
    // the restart is the first request, then the READ
    dut.closedAfter.toList should equal(List(1))
    dut.requests.map(_.function).filter(_ != AppFunction.confirm).take(2).toList should equal(
      List(AppFunction.coldRestart, AppFunction.read))
  }

  test("the restart response must be a single time delay object header") {
    waitFor("33 01 07 01 00 00 00 00 00 00") should equal(None) // another object
    waitFor("34 02 07 01 00 00 34 02 07 01 00 00") should equal(None) // two
    waitFor("") should equal(None)
  }

  test("3.1r1 enables cold restart first, then disables it and expects IIN2.0, then enables it again") {
    class Plugin extends DefaultIntegrationPlugin {
      var enabled = false
      var settings: List[Boolean] = Nil
      override def setColdRestartSupport(reporter: PluginReporter, enabled: Boolean): Unit = {
        this.enabled = enabled
        settings :+= enabled
      }
    }
    // restarts on a cold restart while the plugin has it enabled, or always if it ignores the setting, and rejects it
    // with IIN2.0 otherwise; reports IIN1.7 until a write clears it
    def run(ignoresSetting: Boolean): (Boolean, List[Boolean]) = {
      val plugin = new Plugin
      var restarted = false
      val dut = new ScriptedDut(
        StubDriver.reporter(plugin),
        request =>
          if (request.function == AppFunction.coldRestart) {
            if (plugin.enabled || ignoresSetting) {
              restarted = true
              (IIN.empty, Message.parse(Hex.from("C0 81 00 00 34 02 07 01 F4 01")).headers)
            } else (IIN.FUNC_NOT_SUPPORTED, Nil)
          } else {
            if (request.function == AppFunction.write) restarted = false
            (if (restarted) IIN.DEVICE_RESTART else IIN.empty, Nil)
          }
      ) { override def close(): Unit = () }
      val passed = Run.passes(new ColdRestart(configures = true), dut)
      (passed, plugin.settings)
    }
    run(ignoresSetting = false) should equal((true, List(true, false, true)))
    // which fails step 12, but still has cold restart turned back on
    run(ignoresSetting = true) should equal((false, List(true, false, true)))
  }
}
