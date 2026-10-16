// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ LinkOptions, SupportOptions, TestOptions, UnsolicitedOptions }
import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.UnconfirmedResponse
import io.stepfunc.dnp3c.conformance.common.UnsolicitedResponseSteps.expectNoEventsUnsolicited
import io.stepfunc.dnp3c.conformance.common.Validations
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.{ AutoConfirm, Comment, DeferredCheck, LinkOverride, Requests, TestDriver }
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE

object Procedure8_11_2_1 extends ProcedureDefinition {

  override def requiredOptions: List[OptionCondition] = List(SupportOptions.unsolicitedResponse)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    val unsolConfirmTimeoutMs = options.get(UnsolicitedOptions.unsolicitedConfirmTimeoutMs)
    val holdOffMs = options.get(UnsolicitedOptions.holdOffMs)
    val toleranceMs = options.get(TestOptions.timingToleranceMs)
    val restartResponse = new UnconfirmedResponse(unsolConfirmTimeoutMs, toleranceMs)
    val nullWithRestart = Validations.containing(IIN.DEVICE_RESTART) && Validations.isNull
    val originalAddress = options.get(LinkOptions.source)
    val otherMasterAddress = options.get(UnsolicitedOptions.otherMasterAddress)

    List(
      1 --> describe("Verify that offline configuration can enable or disable unsolicited responses") +
        PluginSteps.enableUnsolicitedResponse(true),
      2 --> describe(
        "Not verified by this tool: that offline configuration can set the unsolicited confirmation timeout from 1 " +
          "second or less to 1 minute or more"),
      3 --> PluginSteps.setUnsolicitedResponseTimeout(unsolConfirmTimeoutMs),
      4 -->
        describe("Verify that offline configuration can set the destination master address for unsolicited responses") +
        PluginSteps.setMasterAddress(otherMasterAddress),
      5 --> PluginSteps.cyclePower,
      6 --> expectUnsolicitedResponse(
        AutoConfirm.No,
        FrameValidator.none,
        Validations.isNull,
        LinkOverride(None, Some(UInt16LE(otherMasterAddress)))),
      7 --> PluginSteps.setMasterAddress(originalAddress),
      8 --> PluginSteps.cyclePower,
      (9 to 14) --> expectUnsolicitedResponse(restartResponse.hold, nullWithRestart),
      (15 to 18) --> restartResponse.expectRetry(nullWithRestart) + restartResponse.expectRetry(nullWithRestart),
      19 --> send(Requests.clearRestartIIN(Sequence.zero)),
      20 --> expectSingleResponse(Sequence.zero, Validations.isNull && Validations.without(IIN.DEVICE_RESTART)),
      (21 to 22) --> restartResponse.expectRetry(Validations.isNull && Validations.without(IIN.DEVICE_RESTART)),
      23 --> send(Requests.readClass0(Sequence.one)),
      // the procedures pull both ways here (see the README): step 24 and 8.11.1's restart behavior read as an answer
      // within the usual response time, while 8.11.1's unsolicited and polled responses, IEEE 1815-2012 4.6.6 rule 16
      // and TB2015-002a have the DUT defer the READ until the confirmation or its timeout. So step 24 accepts either:
      // an answer deferred to the startup response's confirm timeout, or one well before it, with a warning

      24 --> expectDeferrableAnswer(responseTimeoutMs(options), unsolConfirmTimeoutMs, toleranceMs, restartResponse),
      (25 to 26) --> restartResponse.expectRetry(Validations.isNull, AutoConfirm.Wrong),
      (27 to 28) --> restartResponse.expectRetry(Validations.isNull, AutoConfirm.Yes),
      29 --> PluginSteps.generateUnsolicitedEvents(),
      30 --> expectNoEventsUnsolicited(holdOffMs, unsolConfirmTimeoutMs, toleranceMs)
    )
  }

  // the answer to step 23's READ, which the DUT may defer until the unsolicited response is confirmed or its confirm
  // timeout expires
  private def expectDeferrableAnswer(
      responseTimeoutMs: Int,
      confirmTimeoutMs: Int,
      timingToleranceMs: Int,
      unsolicitedResponse: UnconfirmedResponse): TestStep = new TestStep {
    private val read = expectAtLeastOneResponse(Sequence.one)
    private val timeoutMs = responseTimeoutMs + confirmTimeoutMs + timingToleranceMs

    override def description: List[Comment] =
      s"The DUT may defer its response until the startup unsolicited response is confirmed or its confirm timeout " +
        s"expires, so wait up to $timeoutMs ms for it (the response timeout, the unsolicited confirm timeout and " +
        s"$timingToleranceMs ms)" :: read.description

    override def run(driver: TestDriver): List[DeferredCheck] = {
      driver.readAllResponses(Sequence.one, AutoConfirm.Yes, firstTimeoutMs = Some(timeoutMs))
      val leftMs = unsolicitedResponse.msUntilTimeout(driver)
      driver.reporter.info(
        s"The answer to the READ arrived ${confirmTimeoutMs - leftMs} ms after the unconfirmed unsolicited response, " +
          s"whose confirm timeout is $confirmTimeoutMs ms")
      if (leftMs > timingToleranceMs) {
        driver.reporter.warning(
          "the DUT answered the READ while its startup unsolicited response was unconfirmed; IEEE 1815-2012 4.6.6 " +
            "rule 16, as amended by TB2015-002a, has the answer deferred until the confirmation arrives or the " +
            "confirm timeout expires")
      }
      Nil
    }
  }
}
