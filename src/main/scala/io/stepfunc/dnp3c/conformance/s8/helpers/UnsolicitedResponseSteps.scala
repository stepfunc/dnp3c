// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.helpers

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ SupportOptions, UnsolicitedOptions }
import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3.app.objects.{ EventClassGroupVariation, Group60Var2, Group60Var3, Group60Var4 }
import io.stepfunc.dnp3c.dnp3.app.{ Message, _ }
import io.stepfunc.dnp3c.dnp3.{
  ActionResult, DeferredCheck, ActionTiming, AutoConfirm, Comment, LinkOverride, MessageRecorder, MessageValidation,
  Requests,
  TestDriver
}

object UnsolicitedResponseSteps {

  // how long the DUT can take to send an unsolicited response to new events: its hold-off time, and any wait for the
  // confirmation of an earlier response
  private def eventsTimeoutMs(holdOffMs: Int, unsolConfirmTimeoutMs: Int): Int = holdOffMs + unsolConfirmTimeoutMs + 200

  // no unsolicited response to new events: wait as long as the DUT could take to send one
  private[s8] def expectNoEventsUnsolicited(holdOffMs: Int, unsolConfirmTimeoutMs: Int): TestStep =
    expectNoUnsolicitedResponse(Some(eventsTimeoutMs(holdOffMs, unsolConfirmTimeoutMs)))

  private def expectEventsUnsolicited(holdOffMs: Int, unsolConfirmTimeoutMs: Int)(
      autoConfirm: AutoConfirm,
      validation: MessageValidation = MessageValidation.none): TestStep =
    expectUnsolicitedResponse(
      autoConfirm,
      FrameValidator.none,
      withEvents(validation),
      LinkOverride.default,
      Some(eventsTimeoutMs(holdOffMs, unsolConfirmTimeoutMs)))

  // a response to new events carries them: checks that compare it with other responses would otherwise pass on a
  // response without any
  private def withEvents(validation: MessageValidation): MessageValidation =
    MessageValidation.check("Verify that the response contains events") { message =>
      if (message.measurements(PointType.Event).isEmpty) throw new Exception("Expected events in the response")
    } && validation

  /**
   * Generate events, then read the unsolicited response they trigger. A plugin that can't act instantly (a manual
   * tester) is armed first and acts within actionWindowMs, so the harness is already reading when the DUT responds,
   * and times the response from when it actually arrives.
   */
  private[helpers] def generateAndExpectEvents(holdOffMs: Int, unsolConfirmTimeoutMs: Int, actionWindowMs: Int)(
      autoConfirm: AutoConfirm,
      validation: MessageValidation = MessageValidation.none): TestStep = new TestStep {

    override def description: List[Comment] =
      PluginSteps.generateUnsolicitedEvents().description ++
        expectEventsUnsolicited(holdOffMs, unsolConfirmTimeoutMs)(autoConfirm, validation).description

    override def run(driver: TestDriver): List[DeferredCheck] = {
      val action = driver.reporter.plugin.generateUnsolicitedEvents(
        driver.reporter.pluginReporter(),
        ActionTiming.ArmThenAct(actionWindowMs))
      val (extraMs, noResponse) = action match {
        case ActionResult.Performed => (0, "No unsolicited response received.")
        case ActionResult.Armed(withinMs) =>
          (
            withinMs,
            s"No unsolicited response within $withinMs ms of arming, plus the DUT's time to respond. If the events " +
              "were generated in time, the DUT didn't respond to them; otherwise rerun the procedure.")
      }
      expectUnsolicitedResponse(
        autoConfirm,
        FrameValidator.none,
        withEvents(validation),
        LinkOverride.default,
        Some(eventsTimeoutMs(holdOffMs, unsolConfirmTimeoutMs) + extraMs),
        noResponse
      ).run(driver)
    }
  }

  // 8.11.2.2 to 8.11.2.4: unsolicited responses for one event class; a procedure object gives its id and description
  abstract class UnsolicitedClass(data: EventClassData) extends TestProcedure {
    override def requiredOptions: List[OptionCondition] = List(SupportOptions.unsolicitedResponse)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = classSteps(
      data,
      options.get(data.support),
      otherSupportedClasses(data, options),
      options.get(UnsolicitedOptions.unsolicitedConfirmTimeoutMs),
      options.get(UnsolicitedOptions.holdOffMs)
    )
  }

  // the classes other than data's that the DUT supports, whose events step 16 generates
  private[helpers] def otherSupportedClasses(data: EventClassData, options: OptionSource): List[EventClass] =
    List(EventClassData.class1, EventClassData.class2, EventClassData.class3)
      .filter(other => other != data && options.get(other.support))
      .map(_.eventClass)

  private def classSteps(
      data: EventClassData,
      supportsClass: Boolean,
      otherClasses: List[EventClass],
      unsolConfirmTimeoutMs: Int,
      holdOffMs: Int): List[LabeledStep] = {
    val unconfirmed = new UnconfirmedResponse(unsolConfirmTimeoutMs)
    val eventClass = data.eventClass
    val classVariation = data.variation
    // step 12: a retry carries at least the events of the response it repeats; analog events may carry current values,
    // and new events may have been generated
    val recorder = new MessageRecorder
    val sameEvents = PluginValidations.verifyResponseRetainsEvents(recorder)

    val steps1to6 = List(
      0 --> PluginSteps.enableUnsolicitedResponse(true) + PluginSteps.cyclePower +
        confirmUnsolicitedNullResponse, // Extra step so that we can run this test alone
      1 --> send(disableUnsolicitedResponses(Sequence.zero)),
      2 --> expectNullResponse(Sequence.zero),
      3 -->
        (if (supportsClass) PluginSteps.generateClassEvents(eventClass)
         else describe(s"Device cannot generate Class ${classVariation.clazz} data")),
      4 --> expectNoEventsUnsolicited(holdOffMs, unsolConfirmTimeoutMs),
      5 --> send(enableUnsolicitedResponses(Sequence.one, classVariation)),
      6 --> expectNullResponse(Sequence.one)
    )

    val steps7to15 = if (supportsClass) List(
      // the retries are timed from this response and the next, so a manual answer here waits until the steps are done
      (7 to 10) --> expectEventsUnsolicited(holdOffMs, unsolConfirmTimeoutMs)(
        unconfirmed.hold,
        PluginValidations.verifyOldAndNewClassEvents(eventClass).deferrable && recorder.record),
      (11 to 13) --> unconfirmed.expectRetry(sameEvents.deferrable && recorder.record, AutoConfirm.Wrong),
      (14 to 15) --> unconfirmed.expectRetry(sameEvents, AutoConfirm.Yes)
    )
    else List(
      (7 to 15) --> describe(s"Device cannot generate Class ${classVariation.clazz} events")
    )

    val steps16to17 = List(
      16 --> PluginSteps.generateClassEvents(otherClasses),
      17 --> expectNoEventsUnsolicited(holdOffMs, unsolConfirmTimeoutMs)
    )

    steps1to6 ++ steps7to15 ++ steps16to17
  }

  def unsolAndPolledResponsesSteps(
      unsolConfirmTimeoutMs: Int,
      hasMinNumEventsBeforeUnsolicited: Boolean,
      appConfirmTimeoutMs: Int,
      holdOffMs: Int,
      allowDoubleBit: Boolean,
      actionWindowMs: Int): Iterable[LabeledStep] = {
    val messageRecorder = new MessageRecorder

    val seq = MutableSequence.zero

    def expectEvents(autoConfirm: AutoConfirm, validation: MessageValidation = MessageValidation.none): TestStep =
      expectEventsUnsolicited(holdOffMs, unsolConfirmTimeoutMs)(autoConfirm, validation)
    def generateAndExpect(autoConfirm: AutoConfirm, validation: MessageValidation = MessageValidation.none): TestStep =
      generateAndExpectEvents(holdOffMs, unsolConfirmTimeoutMs, actionWindowMs)(autoConfirm, validation)
    val unconfirmedUnsolicited = new UnconfirmedResponse(unsolConfirmTimeoutMs)
    val unconfirmedPolled = new UnconfirmedResponse(appConfirmTimeoutMs)
    // the retry limit 8.11.2.5.9 configures
    val maxRetries = 5
    // an unsolicited retry comes within a confirm timeout, plus the tolerance expectRetry allows, of the response it
    // repeats, so no response in that window means no more retries; 8.11.2.5.9 only counts its retries, so it doesn't
    // check they come no sooner, as expectRetry does
    val retryWindowMs = Some(unsolConfirmTimeoutMs + UnconfirmedResponse.timingToleranceMs)
    def expectRetry(autoConfirm: AutoConfirm): TestStep = expectUnsolicitedResponse(
      autoConfirm,
      FrameValidator.none,
      MessageValidation.none,
      LinkOverride.default,
      retryWindowMs)
    val expectNoMoreRetries = expectNoUnsolicitedResponse(retryWindowMs)

    // 8.11.2.5.9 steps 2-6, 11-15 and 20-24: restart the DUT, confirm its startup response, clear the restart, and
    // enable unsolicited responses
    def restartAndEnable(first: Int): List[LabeledStep] = List(
      first --> PluginSteps.cyclePower,
      first + 1 --> confirmUnsolicitedNullResponse,
      first + 2 --> send(Requests.clearRestartIIN(seq.increment())) + expectNullResponse(seq),
      first + 3 --> send(enableUnsolicitedResponses(seq.increment())),
      first + 4 --> expectNullResponse(seq)
    )

    // 8.11.2.5.8 also allows a DUT that holds events back for a time instead of until there are enough of them. Only
    // the count threshold is covered: unsolicited.holdOffMs is the longest a DUT may wait, not a guaranteed safe
    // interval, so it can't establish that the READ goes out before the unsolicited response.
    val section811258: Iterable[LabeledStep] = if (hasMinNumEventsBeforeUnsolicited) {
      List(
        // warned before generating events, so nothing waits on the tester between the events and the READ
        1 --> PluginSteps.prepare(
          s"In step 6, after the DUT's next response, you'll have less than $appConfirmTimeoutMs ms (the application " +
            "confirm timeout) to generate events that would trigger an unsolicited response. Get ready, then press " +
            "ENTER.") + PluginSteps.generateNotEnoughUnsolicitedEvents(),
        2 --> send(Requests.readClass123(seq.increment())),
        (3 to 5) --> expectSingleResponse(
          seq,
          unconfirmedPolled.hold,
          // the polled response is held unconfirmed and step 7 is timed, so a manual answer waits until the steps
          // are done
          PluginValidations.verifyLatestClassEvents(EventClass.All).deferrable && verifyConfirmRequested &&
            messageRecorder.record
        ),
        6 --> unconfirmedPolled.generateEventsWhileHeld,
        7 --> unconfirmedPolled.expectNoResponseBeforeTimeout,
        (8 to 11) --> expectEvents(
          AutoConfirm.Yes,
          PluginValidations.verifyResponseRetainsEvents(messageRecorder))
      )
    } else {
      List((1 to 11) --> describe(
        "Skipped because the DUT does not have a minimum number of events before an unsolicited response. A DUT " +
          "that holds events back for a time instead is not covered by this tool."))
    }

    List(
      0 --> PluginSteps.enableUnsolicitedResponse(true) + PluginSteps.cyclePower +
        confirmUnsolicitedNullResponse, // Extra step so that we can run this test alone
      Label("8.11.2.5.1") --> describe("Transmits data-filled unsolicited responses"),
      1 --> send(enableUnsolicitedResponses(seq)),
      2 --> expectNullResponse(seq),
      (3 to 5) --> generateAndExpect(AutoConfirm.Yes, messageRecorder.record),
      Label("8.11.2.5.2") --> describe("Clears transmitted data upon confirmation"),
      1 --> describe("The response in 8.11.2.5.1 was confirmed when it arrived"),
      2 --> send(Requests.readClass123(seq.increment())),
      3 -->
        expectAtLeastOneResponse(seq, PluginValidations.verifyPolledDataDoNotRepeatUnsolicitedData(messageRecorder)),
      Label("8.11.2.5.3") --> describe("Processes non-READ requests immediately"),
      1 --> generateAndExpect(unconfirmedUnsolicited.hold, messageRecorder.record),
      2 --> send(Requests.clearRestartIIN(seq.increment())),
      3 --> expectNullResponse(seq),
      // 8.11.2.5.4 acts immediately after this response, so a manual answer here waits until the steps are done
      (4 to 5) --> unconfirmedUnsolicited.expectRetry(
        PluginValidations.verifyResponseRetainsEvents(messageRecorder).deferrable && messageRecorder.record),
      Label("8.11.2.5.4") --> describe("Defers READ requests until after confirmation received"),
      1 --> send(Requests.readClass123(seq.increment())),
      // half the confirm timeout: well before it expires
      2 --> unconfirmedUnsolicited.expectNoResponseUntil(unsolConfirmTimeoutMs / 2),
      3 --> unconfirmedUnsolicited.confirm,
      (4 to 6) -->
        expectAtLeastOneResponse(seq, PluginValidations.verifyPolledDataDoNotRepeatUnsolicitedData(messageRecorder)),
      Label("8.11.2.5.5") --> describe("Defers READ requests until after confirmation timeout"),
      1 --> generateAndExpect(unconfirmedUnsolicited.hold, messageRecorder.record),
      2 --> send(Requests.readClass123(seq.increment())),
      (3 to 7) --> unconfirmedUnsolicited.expectNoResponseBeforeTimeout +
        expectAtLeastOneResponse(
          seq,
          verifyConfirmRequested &&
            PluginValidations.verifyResponseRetainsEvents(messageRecorder).acrossFragments),
      8 --> expectNoMoreRetries,
      Label("8.11.2.5.6") --> describe("Abandons READ requests upon subsequent non-READ requests"),
      1 --> generateAndExpect(unconfirmedUnsolicited.hold, messageRecorder.record),
      2 --> send(Requests.readClass123(seq.increment())),
      3 --> send(Requests.clearRestartIIN(seq.increment())),
      4 --> expectSingleResponse(seq, without(IIN.errors)),
      // also the unsolicited response 8.11.2.5.7 is deferred against; 8.11.2.5.7 acts immediately after it, so a manual
      // answer here waits until the steps are done
      (5 to 6) --> unconfirmedUnsolicited.expectRetry(
        PluginValidations.verifyResponseRetainsEvents(messageRecorder).deferrable && messageRecorder.record),
      Label("8.11.2.5.7") --> describe("Abandons READ requests upon subsequent READ requests"),
      1 --> send(Requests.readClass0(seq.increment())),
      2 --> send(Requests.readClass123(seq.increment())),
      (3 to 8) --> unconfirmedUnsolicited.expectNoResponseBeforeTimeout +
        expectAtLeastOneResponse(
          seq,
          Validations.verifyOnlyTable84Contents(allowDoubleBit) &&
            verifyConfirmRequested &&
            PluginValidations.verifyResponseRetainsEvents(messageRecorder).acrossFragments
        ),
      9 --> expectNoMoreRetries,
      Label("8.11.2.5.8") --> describe("Inhibits Unsolicited responses until after polled confirmation")
    ) ++
      section811258 ++
      List(
        Label("8.11.2.5.9") --> describe("Retries unsolicited responses configurable number of times"),
        1 --> PluginSteps.setMaxUnsolicitedRetries(Some(maxRetries))
      ) ++
      restartAndEnable(2) ++
      List(
        7 --> PluginSteps.generateUnsolicitedEvents(),
        (8 to 9) --> expectEvents(AutoConfirm.No),
        10 --> repeat(maxRetries)(expectRetry(AutoConfirm.No)) + expectNoMoreRetries
      ) ++
      restartAndEnable(11) ++
      List(
        // Step 18 confirms one response and then expects no more, so retries mustn't pile up before it: the harness is
        // already reading when the events are generated. Steps 8-10 and 26-28 only count retries, so they needn't be.
        (16 to 17) --> generateAndExpect(AutoConfirm.No),
        18 --> expectRetry(AutoConfirm.Yes) + expectNoMoreRetries,
        19 --> PluginSteps.setMaxUnsolicitedRetries(None)
      ) ++
      restartAndEnable(20) ++
      List(
        25 --> PluginSteps.generateUnsolicitedEvents(),
        (26 to 27) --> expectEvents(AutoConfirm.No),
        // "an extended period": more retries than the limit of 5 set at step 1
        28 --> repeat(maxRetries + 2)(expectRetry(AutoConfirm.No))
      )
  }

  def offSteps(unsolConfirmTimeoutMs: Int, holdOffMs: Int): Iterable[LabeledStep] = {
    List(
      1 --> PluginSteps.enableUnsolicitedResponse(false),
      2 --> PluginSteps.cyclePower,
      3 --> expectNoUnsolicitedResponse,
      4 --> send(Requests.clearRestartIIN(Sequence.zero)),
      5 --> expectSingleResponse(Sequence.zero, isNull && without(IIN.DEVICE_RESTART)),
      6 --> expectNoUnsolicitedResponse,
      7 --> send(Requests.readClass123(Sequence.one)),
      8 --> expectAtLeastOneResponse(Sequence.one),
      9 --> PluginSteps.generateUnsolicitedEvents(),
      10 --> expectNoEventsUnsolicited(holdOffMs, unsolConfirmTimeoutMs),
      11 --> send(enableUnsolicitedResponses(Sequence.two)),
      12 --> expectSingleResponse(Sequence.two, containing(IIN.FUNC_NOT_SUPPORTED)),
      13 --> expectNoEventsUnsolicited(holdOffMs, unsolConfirmTimeoutMs),
      14 --> send(disableUnsolicitedResponses(Sequence.three)),
      15 --> expectSingleResponse(Sequence.three, containing(IIN.FUNC_NOT_SUPPORTED))
    )
  }

  private def disableUnsolicitedResponses(seq: Sequence): Message =
    Requests.allObjects(AppFunction.disableUnsolicited, seq, Group60Var2, Group60Var3, Group60Var4)

  private def enableUnsolicitedResponses(seq: Sequence): Message =
    Requests.allObjects(AppFunction.enableUnsolicited, seq, Group60Var2, Group60Var3, Group60Var4)

  private def enableUnsolicitedResponses(seq: Sequence, classVariation: EventClassGroupVariation): Message =
    Requests.allObjects(AppFunction.enableUnsolicited, seq, classVariation)
}
