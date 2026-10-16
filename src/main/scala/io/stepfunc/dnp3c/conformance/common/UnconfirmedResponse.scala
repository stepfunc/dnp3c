// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.common

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.common.GenericSteps.validateUnsolicitedResponse
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.{ ActionTiming, AutoConfirm, Comment, MessageValidation, Requests, TestDriver }
import io.stepfunc.dnp3c.dnp3.DeferredCheck

// A response left unconfirmed, to check that the DUT holds back what it would otherwise send (e.g. the answer to a
// READ) until the response is confirmed or its confirm timeout expires. Timing is measured from when the response
// was received, on the driver's clock.
class UnconfirmedResponse(confirmTimeoutMs: Int, timingToleranceMs: Int) {
  private var response: Option[Message] = None
  private var receivedAtMs = 0L

  // the AutoConfirm for reading the response: remember it, but don't confirm it
  val hold: AutoConfirm =
    new AutoConfirm.Custom((msg, driver) => record(msg, driver), List("Leave the response unconfirmed for now"))

  def record(msg: Message, driver: TestDriver): Unit = {
    response = Some(msg)
    receivedAtMs = driver.clock.nowMs()
  }

  // Expect the DUT to retransmit the unsolicited response once its confirm timeout expires, within the timing
  // tolerance, i.e. neither more nor less often than once every confirm timeout (8.11.2.1)
  def expectRetry(validation: MessageValidation, autoConfirm: AutoConfirm = AutoConfirm.No): TestStep = new TestStep {
    override def description: List[Comment] =
      s"Expect a retry $confirmTimeoutMs ms (+/- $timingToleranceMs ms) after the previous unsolicited response" ::
        validation.description ::: autoConfirm.description

    override def run(driver: TestDriver): List[DeferredCheck] = {
      val previousMs = receivedAtMs
      driver.readMessage(Unsolicited.Yes, timeoutMs = Some(msUntilTimeout(driver) + timingToleranceMs)) match {
        case None => throw new Exception(s"No retry within $confirmTimeoutMs ms (+ $timingToleranceMs ms)")
        case Some(msg) =>
          record(msg, driver)
          val intervalMs = receivedAtMs - previousMs
          if (intervalMs < confirmTimeoutMs - timingToleranceMs) {
            throw new Exception(
              s"Retry after $intervalMs ms, expected $confirmTimeoutMs ms (+/- $timingToleranceMs ms)")
          }
          autoConfirm.execute(msg, driver)
          validateUnsolicitedResponse(msg)
          validation.validate(driver.reporter, msg)
      }
    }
  }

  def expectNoResponseUntil(msBeforeTimeout: Int): TestStep = new TestStep {
    override def description: List[Comment] =
      List(s"Expect no message until $msBeforeTimeout ms before the confirm timeout")

    override def run(driver: TestDriver): List[DeferredCheck] = {
      val remainingMs = msUntilTimeout(driver) - msBeforeTimeout
      if (remainingMs <= 0) {
        throw new Exception(
          "The time before the confirm timeout ran out during earlier steps (e.g. while generating events), so it " +
            "could not be checked that the DUT holds back its message. Configure a longer confirm timeout on the DUT " +
            "(and in the matching option) or automate the earlier steps.")
      }
      if (driver.readMessage(Unsolicited.No, timeoutMs = Some(remainingMs)).isDefined) {
        throw new Exception("Received a message before the confirm timeout")
      }
      Nil
    }
  }

  // Expect no message until the confirm timeout expires (within the timing tolerance), then wait for it to expire so
  // that a following read gets its full response timeout
  def expectNoResponseBeforeTimeout: TestStep = expectNoResponseUntil(margin) + waitForTimeout

  private def waitForTimeout: TestStep = new TestStep {
    override def description: List[Comment] = List("Wait for the confirm timeout to expire")

    override def run(driver: TestDriver): List[DeferredCheck] = {
      driver.clock.sleep(math.max(0, msUntilTimeout(driver)))
      Nil
    }
  }

  // the time left until the response's confirm timeout expires, negative once it has
  def msUntilTimeout(driver: TestDriver): Int = (receivedAtMs + confirmTimeoutMs - driver.clock.nowMs()).toInt

  // the margin before the confirm timeout that expectNoResponseBeforeTimeout checks no message arrives in
  private def margin: Int = math.min(timingToleranceMs, confirmTimeoutMs / 4)

  // Generate events while this response is held unconfirmed, early enough for expectNoResponseBeforeTimeout to check
  // that the DUT holds them back. A late action fails there, as the window ran out.
  def generateEventsWhileHeld: TestStep = new TestStep {
    override def description: List[Comment] = List(
      "Before the confirm timeout, generate events that would otherwise trigger an unsolicited response")

    override def run(driver: TestDriver): List[DeferredCheck] = {
      val remainingMs = msUntilTimeout(driver) - margin
      if (remainingMs <= 0) {
        throw new Exception(
          "The confirm timeout ran out before the events could be generated. Rerun the procedure, with a longer " +
            "confirm timeout on the DUT (and in the matching option) if the steps can't be done in time.")
      }
      driver.reporter.plugin.generateUnsolicitedEvents(
        driver.reporter.pluginReporter(),
        ActionTiming.ActBefore(remainingMs))
      Nil
    }
  }

  def confirm: TestStep = new TestStep {
    override def description: List[Comment] = List("Confirm the response")

    override def run(driver: TestDriver): List[DeferredCheck] = {
      response.foreach(msg => driver.writeMessage(Requests.confirm(msg.ctrl.seq, msg.ctrl.uns)))
      Nil
    }
  }
}
