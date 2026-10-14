// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ SupportOptions, TimeSyncOptions }
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.Validations
import io.stepfunc.dnp3c.conformance.common.Validations._
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects._

/**
 * 2.7's 8.15.2, 3.1r1's 8.15.3
 *
 * @param conditionStep whether step 1 states that the DUT reports events with relative time, as in 2.7; 3.1r1 has no
 *   such step, which moves the others back one
 * @param reportsRelativeTime when the DUT reports binary input events with relative time, which the test needs
 */
class RelativeTimeSupported(
    conditionStep: Boolean,
    reportsRelativeTime: Requirement = OptionCondition(TimeSyncOptions.binaryInput.withRelativeTime))
    extends ProcedureDefinition {

  override protected def requirement: Requirement =
    Requirement.all((SupportOptions.Inputs.binary.withEvents :+ reportsRelativeTime)*)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    val sequence = MutableSequence.zero
    val o = if (conditionStep) 0 else -1

    val steps1To9 =
      Option.when(conditionStep)(1 --> describe("DUT supports Binary Input Change with Relative Time.")).toList ++ List(
        2 + o --> PluginSteps.cyclePower,
        3 + o --> send(Requests.clearRestartIIN(sequence)) + expectNullResponse(sequence),
        4 + o --> PluginSteps.generateBinaryInputChangeEvents(),
        5 + o --> send(Requests.readAllObjects(sequence.increment(), Group2Var3)),
        (6 + o to 9 + o) --> expectSingleResponse(
          sequence,
          AutoConfirm.Yes,
          ctoResponse(timeSync = false) &&
            PluginValidations.verifyLatestBinaryInputChangeEvents()
        )
      )

    val steps10To38 = if (options.get(TimeSyncOptions.supportSynchronizingTimeFromMaster)) {
      List(
        10 + o --> describe("Device supports time synchronization from DNP3 master"),
        11 + o --> delay(options.get(TimeSyncOptions.maxTimeFromResetToIIN14Ms)),
        12 + o --> send(AppFunction.delayMeas, sequence.increment(), List()),
        13 + o --> expectSingleResponse(sequence, isDelayMeasurement && Validations.containing(IIN.NEED_TIME)),
        14 + o --> writeCurrentTime(sequence.increment()),
        15 + o --> expectSingleResponse(sequence, Validations.isNull && Validations.without(IIN.NEED_TIME)),
        16 + o --> PluginSteps.generateBinaryInputChangeEvents(),
        17 + o --> send(Requests.readAllObjects(sequence.increment(), Group2Var3)),
        (18 + o to 21 + o) --> expectSingleResponse(
          sequence,
          AutoConfirm.Yes,
          ctoResponse(timeSync = true) &&
            PluginValidations.verifyLatestBinaryInputChangeEvents()
        ),
        22 + o --> PluginSteps.generateBinaryInputChangeEvents(),
        23 + o --> delay(options.get(TimeSyncOptions.maxTimeBetweenSyncRequestsMs)),
        24 + o --> send(AppFunction.delayMeas, sequence.increment(), List()),
        25 + o --> expectSingleResponse(sequence, isDelayMeasurement && Validations.containing(IIN.NEED_TIME)),
        26 + o --> send(Requests.readAllObjects(sequence.increment(), Group2Var3)),
        (27 + o to 29 + o) --> expectSingleResponse(
          sequence,
          AutoConfirm.No,
          ctoResponse(timeSync = true) &&
            // the response is left unconfirmed while steps 30-32 write the time, so a manual answer comes after
            PluginValidations.verifyLatestBinaryInputChangeEvents().deferrable
        ),
        30 + o --> describe("Withhold the application layer confirmation"),
        31 + o --> writeCurrentTime(sequence.increment()),
        32 + o --> expectSingleResponse(sequence, Validations.isNull && Validations.without(IIN.NEED_TIME)),
        33 + o --> send(Requests.readAllObjects(sequence.increment(), Group2Var3)),
        (34 + o to 36 + o) --> expectSingleResponse(
          sequence,
          AutoConfirm.Yes,
          ctoResponse(timeSync = true) &&
            PluginValidations.verifyLatestBinaryInputChangeEvents()
        ),
        37 + o --> send(Requests.readAllObjects(sequence.increment(), Group2Var3)),
        38 + o --> expectNullResponse(sequence)
      )
    } else {
      List(
        (10 + o to 38 + o) --> describe("Device does not support time synchronization from DNP3 master")
      )
    }

    steps1To9 ++ steps10To38
  }

  // g2v3 events after a CTO, unsynchronized (g51v2) before the time is set and synchronized (g51v1) after, in a
  // response that asks for confirmation, with flags as Note 5 requires
  private def ctoResponse(timeSync: Boolean): MessageValidation =
    eventsWithCtos(List(Group2Var3), Set(if (timeSync) Group51Var1 else Group51Var2)) &&
      verifyFlagsComplyWithFlagNote &&
      verifyConfirmRequested

}

object Procedure8_15_2 extends RelativeTimeSupported(conditionStep = true)
