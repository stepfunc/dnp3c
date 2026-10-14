// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.common

import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class TimedActionTest extends AnyFunSuite with Matchers {

  // records the timing each event generation is asked with, and answers as a manual or automated plugin would
  private class Plugin(result: ActionTiming => ActionResult) extends DefaultIntegrationPlugin {
    val timings = mutable.ListBuffer.empty[ActionTiming]
    override def generateUnsolicitedEvents(reporter: PluginReporter, timing: ActionTiming): ActionResult = {
      timings += timing
      result(timing)
    }
  }

  // a DUT that never sends anything; records the read timeouts
  private class Driver(plugin: IntegrationPlugin) extends StubDriver(StubDriver.reporter(plugin)) {
    val readTimeouts = mutable.ListBuffer.empty[Option[Int]]
    override def readMessage(
        unsolicited: Unsolicited,
        validator: FrameValidator,
        linkOverride: LinkOverride,
        timeoutMs: Option[Int]): Option[Message] = {
      readTimeouts += timeoutMs
      None
    }
    override def close(): Unit = {}
  }

  private val step = UnsolicitedResponseSteps.generateAndExpectEvents(holdOffMs = 0, 2000, actionWindowMs = 10000)(
    AutoConfirm.No)

  test("an armed manual action gives the DUT's response the action window as well") {
    val plugin = new Plugin({
      case ActionTiming.ArmThenAct(ms) => ActionResult.Armed(ms)
      case _ => ActionResult.Performed
    })
    val driver = new Driver(plugin)
    an[Exception] should be thrownBy step.run(driver)
    plugin.timings.toList should equal(List(ActionTiming.ArmThenAct(10000)))
    driver.readTimeouts.toList should equal(List(Some(12200)))
  }

  test("an action performed before returning, as an automated plugin's is, gets no extra time") {
    val driver = new Driver(new Plugin(_ => ActionResult.Performed))
    an[Exception] should be thrownBy step.run(driver)
    driver.readTimeouts.toList should equal(List(Some(2200)))
  }

  test("an action inside a held response's window is given the time left, or isn't asked once it has run out") {
    val plugin = new Plugin(_ => ActionResult.Performed)
    val driver = new Driver(plugin)
    val held = new UnconfirmedResponse(2000)
    held.record(Message(Control.request(Sequence.zero), AppFunction.response, Some(IIN.empty), Nil), driver)

    driver.clock.advance(100)
    held.generateEventsWhileHeld.run(driver)
    // 2000 ms confirm timeout, 100 ms gone, and a 500 ms margin for the following check
    plugin.timings.toList should equal(List(ActionTiming.ActBefore(1400)))

    driver.clock.advance(1500)
    an[Exception] should be thrownBy held.generateEventsWhileHeld.run(driver)
    plugin.timings should have size 1
  }
}
