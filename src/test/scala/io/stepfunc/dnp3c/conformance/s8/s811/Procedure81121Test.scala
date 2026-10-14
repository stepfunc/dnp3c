// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s811

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.api.Options
import io.stepfunc.dnp3c.conformance.api.options.UnsolicitedOptions
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class Procedure81121Test extends AnyFunSuite with Matchers {

  private class Plugin extends DefaultIntegrationPlugin {
    var eventsGenerated = false
    override def cyclePower(reporter: PluginReporter): Unit = ()
    override def enableUnsolicitedResponse(reporter: PluginReporter, enabled: Boolean): Unit = ()
    override def setUnsolicitedResponseTimeout(reporter: PluginReporter, timeoutMs: Int): Unit = ()
    override def setMasterAddress(reporter: PluginReporter, address: Int): Unit = ()
    override def generateUnsolicitedEvents(reporter: PluginReporter, timing: ActionTiming): ActionResult = {
      eventsGenerated = true
      ActionResult.Performed
    }
  }

  // A DUT that sends a null unsolicited response (with DEVICE_RESTART until it's cleared) one confirm timeout after
  // each read for one, except the `early`th, which it sends at once, and nothing once events are generated; it answers
  // requests with null responses. Records the timeouts of the reads for unsolicited responses after the events.
  private class Dut(plugin: Plugin, confirmTimeoutMs: Int, early: Option[Int] = None)
      extends RespondingDriver(StubDriver.reporter(plugin)) {
    private var restart = true
    private var sent = 0
    val timeoutsAfterEvents = mutable.ListBuffer.empty[Option[Int]]

    override def writeMessage(message: Message, options: LinkOverride): Unit = message.function match {
      case AppFunction.confirm =>
      case function =>
        if (function == AppFunction.write) restart = false
        enqueue(Message(Control.request(message.ctrl.seq), AppFunction.response, Some(iin), Nil))
    }
    override def readMessage(
        unsolicited: Unsolicited,
        validator: FrameValidator,
        linkOverride: LinkOverride,
        timeoutMs: Option[Int]): Option[Message] =
      if (!unsolicited.value) super.readMessage(unsolicited, validator, linkOverride, timeoutMs)
      else if (plugin.eventsGenerated) {
        timeoutsAfterEvents += timeoutMs
        timedOut(timeoutMs)
      } else {
        sent += 1
        if (!early.contains(sent)) clock.advance(confirmTimeoutMs)
        Some(
          Message(
            Control(First.Yes, Final.Yes, Confirm.Yes, Unsolicited.Yes, Sequence.zero),
            AppFunction.unsolicited,
            Some(iin),
            Nil))
      }

    private def iin: IIN = if (restart) IIN.DEVICE_RESTART else IIN.empty
  }

  test("step 30 waits for an unsolicited response to the events as long as the DUT could take to send one") {
    val plugin = new Plugin
    val dut = new Dut(plugin, 5000)
    val options = Options.from(Map(UnsolicitedOptions.holdOffMs.id -> "3000"))
    Run.passing(Procedure81121, dut, options)
    // the hold-off, the confirm timeout and a 200 ms margin
    dut.timeoutsAfterEvents.toList should equal(List(Some(8200)))
  }

  test("each unsolicited response after the first is a retry, one confirm timeout after the one before") {
    def passes(early: Option[Int]) = {
      val dut = new Dut(new Plugin, 5000, early)
      Run.passes(Procedure81121, dut)
    }
    passes(None) should equal(true)
    // steps 15 to 18 (the 3rd and 4th responses), 21 (the 5th), 25 (the 6th) and 27 (the 7th)
    for (n <- 3 to 7) withClue(s"response $n")(passes(Some(n)) should equal(false))
  }
}
