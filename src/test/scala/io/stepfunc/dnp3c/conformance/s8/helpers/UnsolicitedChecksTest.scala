// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.helpers

import io.stepfunc.dnp3c.conformance.api.Options
import io.stepfunc.dnp3c.conformance.api.options.UnsolicitedOptions
import io.stepfunc.dnp3c.conformance.s8.s811.Procedure81125
import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.conformance.s8.helpers.UnconfirmedResponse
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import java.io.StringReader
import scala.collection.mutable
import scala.util.Try

@RunWith(classOf[JUnitRunner])
class UnsolicitedChecksTest extends AnyFunSuite with Matchers {

  private def message(function: AppFunction, uns: Boolean): Message = Message(
    Control(First.Yes, Final.Yes, Confirm.Yes, Unsolicited(uns), Sequence.zero),
    function,
    Some(IIN.empty),
    Nil)

  private val unsolicited = message(AppFunction.unsolicited, uns = true)

  // answers each read with the next message, after `elapse` moves the clock on
  private class Dut(
      messages: List[Option[Message]],
      elapseMs: Long = 0,
      plugin: IntegrationPlugin = new DefaultIntegrationPlugin())
      extends StubDriver(StubDriver.reporter(plugin)) {
    private var pending = messages
    val timeouts = mutable.ListBuffer.empty[Option[Int]]
    override def readMessage(
        unsolicited: Unsolicited,
        validator: FrameValidator,
        linkOverride: LinkOverride,
        timeoutMs: Option[Int]): Option[Message] = {
      timeouts += timeoutMs
      clock.advance(elapseMs)
      val next = pending.head
      pending = pending.tail
      next
    }
    override def writeMessage(message: Message, options: LinkOverride): Unit = ()
  }

  test("an unsolicited response must use the unsolicited response function") {
    val step = GenericSteps.expectUnsolicitedResponse(AutoConfirm.No)
    noException should be thrownBy step.run(new Dut(List(Some(unsolicited))))
    an[Exception] should be thrownBy step.run(new Dut(List(Some(message(AppFunction.response, uns = true)))))
    an[Exception] should be thrownBy step.run(new Dut(List(Some(message(AppFunction.unsolicited, uns = false)))))
  }

  test("a solicited response must not have UNS set") {
    val step = GenericSteps.expectSingleResponse(Sequence.zero)
    noException should be thrownBy step.run(new Dut(List(Some(message(AppFunction.response, uns = false)))))
    an[Exception] should be thrownBy step.run(new Dut(List(Some(message(AppFunction.response, uns = true)))))
  }

  test("a retry must come one confirm timeout after the response, within the tolerance") {
    def retryAfter(intervalMs: Option[Long]): (Boolean, Option[Int]) = {
      val held = new UnconfirmedResponse(2000)
      val dut = new Dut(List(intervalMs.map(_ => unsolicited)), intervalMs.getOrElse(2500L))
      held.record(unsolicited, dut)
      val passed = scala.util.Try(held.expectRetry(MessageValidation.none).run(dut)).isSuccess
      (passed, dut.timeouts.head)
    }

    retryAfter(Some(2000)) should equal((true, Some(2500)))
    retryAfter(Some(1600)) should equal((true, Some(2500)))
    retryAfter(Some(1400))._1 should equal(false)
    retryAfter(None)._1 should equal(false)
  }

  test("8.11.2.5.3 and 8.11.2.5.6 expect each retry one confirm timeout after the unconfirmed response") {
    // sends an unsolicited response carrying an event `delayMs` after each read for one, and answers each request
    // with a Null Response
    val plugin = new DefaultIntegrationPlugin {
      override def cyclePower(reporter: PluginReporter): Unit = ()
      override def enableUnsolicitedResponse(reporter: PluginReporter, enabled: Boolean): Unit = ()
      override def generateUnsolicitedEvents(reporter: PluginReporter, timing: ActionTiming): ActionResult =
        ActionResult.Performed
    }
    class Responder(delayMs: Long) extends StubDriver(StubDriver.reporter(plugin)) {
      private val solicited = mutable.Queue.empty[Message]
      override def writeMessage(message: Message, options: LinkOverride): Unit =
        if (message.function != AppFunction.confirm)
          solicited.enqueue(Message.parse(Hex.from("%02X 81 00 00".format(0xC0 | message.ctrl.seq.value))))
      override def readMessage(
          unsolicited: Unsolicited,
          validator: FrameValidator,
          linkOverride: LinkOverride,
          timeoutMs: Option[Int]): Option[Message] =
        if (unsolicited.value) {
          clock.advance(delayMs)
          Some(Message.parse(Hex.from("F0 82 00 00 02 01 17 01 05 81")))
        } else if (solicited.isEmpty) timedOut(timeoutMs)
        else Some(solicited.dequeue())
    }
    // runs every step, carrying on past failures; a manual action gets no answer (end of input), so it fails
    def failedSteps(delayMs: Long): Int = {
      val dut = new Responder(delayMs)
      Console.withIn(new StringReader(""))(
        Procedure81125.configure(Options.from(Map())).steps.count(s => Try(s.step.run(dut)).isFailure))
    }
    // the default confirm timeout is 5000 ms: an unsolicited response at once can't be either retry
    failedSteps(0) should equal(failedSteps(5000) + 2)
  }

  test("8.11.2.5 reads each retry, and expects none after the last, within the confirm timeout and the tolerance") {
    // answers every read at once, an unsolicited one with an event, and records the unsolicited reads' timeouts, and
    // whether 8.11.2.5.9 step 1 has set the retry limit
    class Plugin extends DefaultIntegrationPlugin {
      var retryLimitSet = false
      override def cyclePower(reporter: PluginReporter): Unit = ()
      override def enableUnsolicitedResponse(reporter: PluginReporter, enabled: Boolean): Unit = ()
      override def setMaxUnsolicitedRetries(reporter: PluginReporter, numRetries: Option[Int]): Unit =
        retryLimitSet = true
      override def generateUnsolicitedEvents(reporter: PluginReporter, timing: ActionTiming): ActionResult =
        ActionResult.Performed
    }
    val plugin = new Plugin
    val waits = mutable.ListBuffer.empty[(Boolean, Option[Int])]
    val dut = new StubDriver(StubDriver.reporter(plugin)) {
      private val solicited = mutable.Queue.empty[Message]
      override def writeMessage(message: Message, options: LinkOverride): Unit =
        if (message.function != AppFunction.confirm)
          solicited.enqueue(Message.parse(Hex.from("%02X 81 00 00".format(0xC0 | message.ctrl.seq.value))))
      override def readMessage(
          unsolicited: Unsolicited,
          validator: FrameValidator,
          linkOverride: LinkOverride,
          timeoutMs: Option[Int]): Option[Message] =
        if (unsolicited.value) {
          waits += ((plugin.retryLimitSet, timeoutMs))
          Some(Message.parse(Hex.from("F0 82 00 00 02 01 17 01 05 81")))
        } else if (solicited.isEmpty) timedOut(timeoutMs)
        else Some(solicited.dequeue())
    }
    val options = Options.from(
      Map(UnsolicitedOptions.unsolicitedConfirmTimeoutMs.id -> "2000", UnsolicitedOptions.holdOffMs.id -> "1000"))
    Console.withIn(new StringReader(""))(Procedure81125.configure(options).steps.foreach(s => Try(s.step.run(dut))))
    def inRetryWindow(retryLimitSet: Boolean) = waits.count(_ == (retryLimitSet, Some(2500)))
    // 8.11.2.5.3 and 8.11.2.5.6: a retry each; 8.11.2.5.5 step 8 and 8.11.2.5.7 step 9: no more
    inRetryWindow(false) should equal(4)
    // 8.11.2.5.9 step 10: five retries and no sixth; step 18: a retry and no more; step 28: seven retries
    inRetryWindow(true) should equal(15)
  }

  test("the unsolicited response to generated events must carry them") {
    val plugin = new DefaultIntegrationPlugin {
      override def generateUnsolicitedEvents(reporter: PluginReporter, timing: ActionTiming): ActionResult =
        ActionResult.Performed
    }
    val step = UnsolicitedResponseSteps.generateAndExpectEvents(holdOffMs = 0, 2000, actionWindowMs = 0)(AutoConfirm.No)
    def dut(response: Message) = new Dut(List(Some(response)), plugin = plugin)
    def withObjects(objects: String) =
      unsolicited.copy(headers = Message.parse(Hex.from("C0 81 00 00 " + objects)).headers)
    noException should be thrownBy step.run(dut(withObjects("02 01 17 01 05 81")))
    an[Exception] should be thrownBy step.run(dut(unsolicited))
    an[Exception] should be thrownBy step.run(dut(withObjects("33 01 07 01 00 00 00 00 00 00"))) // a CTO alone
  }
}
