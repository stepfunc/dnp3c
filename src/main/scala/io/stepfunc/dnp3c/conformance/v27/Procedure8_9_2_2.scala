// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.v31r1.Rules
import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ OutstationOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.UnconfirmedResponse
import io.stepfunc.dnp3c.conformance.common.Validations._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.{ Comment, Requests, TestDriver }
import io.stepfunc.dnp3c.dnp3.DeferredCheck

object Procedure8_9_2_2 extends ProcedureDefinition {

  // 8.9.1.2 step 7: a device that cannot produce multiple fragments needs no further fragmentation testing
  override def requiredOptions = List(SupportOptions.multiFragmentResponses)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {

    val confirmTimeout = options.get(OutstationOptions.appConfirmTimeout)
    val pending = new UnconfirmedResponse(confirmTimeout)
    // past the application confirmation timeout: a second longer
    val pastConfirmTimeout = confirmTimeout + 1000
    // inside the application confirmation timeout: half of it
    val withinConfirmTimeout = confirmTimeout / 2
    // a confirm that matches no fragment: 41 is 9 in the 4-bit sequence field, and the fragments here are 0 to 3
    val wrongSequence = Sequence(41)

    List(
      1 --> PluginSteps.generateMultiFragmentResponse() + send(Requests.readClass1230(Sequence.zero)),
      (2 to 4) --> readResponseFragment(Sequence.zero, isFirstOfSeveralFragments && verifyConfirmRequested),
      5 --> delay(pastConfirmTimeout),
      6 --> expectNoResponse,
      7 --> confirm(Sequence.zero),
      8 --> expectNoResponse,
      9 --> send(Requests.readClass1230(Sequence.one)),
      10 --> readResponseFragment(Sequence.one, isFirstOfSeveralFragments && verifyConfirmRequested),
      11 --> expectNoResponseFor(withinConfirmTimeout),
      12 --> confirm(Sequence.one),
      (13 to 15) --> readSecondFragment(pending),
      16 --> confirm(wrongSequence),
      17 --> delay(pastConfirmTimeout),
      18 --> expectNoResponse,
      19 --> pending.confirm,
      20 --> expectNoResponse
    )
  }

  // Steps 13-15: read the second fragment. If it is the last one, confirm it and request multi-fragment data again
  // (step 15), so that steps 16-20 always act on an unconfirmed fragment with more to follow.
  private[v27] def readSecondFragment(pending: UnconfirmedResponse): TestStep = new TestStep {
    override def description: List[Comment] = List(
      "Read the second fragment: FIR clear, and CON set if a third fragment follows",
      "If it is the last fragment, confirm it and request multi-fragment data again"
    )

    override def run(driver: TestDriver): List[DeferredCheck] = {
      // whether the last fragment asks for confirmation is up to the outstation here (IEEE 1815-2012 4.2.2.4.3), and
      // 3.1r1's steps allow either, so its rule against unrequired confirmations doesn't apply to this read
      val second = driver.policies.exempting(Set(Rules.confirmOnlyWhenRequired.id))(readFragment(driver, Sequence.two))
      if (second.ctrl.fir.value) throw new Exception("Expected FIR bit not set")
      val fragment = if (second.ctrl.fin.value) {
        driver.writeMessage(Requests.confirm(Sequence.two, Unsolicited.No))
        driver.reporter.plugin.generateMultiFragmentResponse(driver.reporter.pluginReporter())
        driver.writeMessage(Requests.readClass1230(Sequence.three))
        val first = readFragment(driver, Sequence.three)
        isFirstOfSeveralFragments.validate(driver.reporter, first)
        first
      } else second
      verifyConfirmRequested.validate(driver.reporter, fragment)
      pending.record(fragment, driver)
      Nil
    }
  }

  private def readFragment(driver: TestDriver, seq: Sequence): Message = {
    val msg = driver.readMessage(Unsolicited.No).getOrElse(throw new Exception("Expected a response fragment"))
    TestDriver.checkSolicited(msg, seq)
    msg
  }
}
