// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s815

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ SupportOptions, TimeSyncOptions }
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects._

object Procedure8152 extends ProcedureDefinition {

  override def requiredOptions: List[OptionCondition] =
    SupportOptions.Inputs.binary.withEvents :+ OptionCondition(TimeSyncOptions.binaryInputChangeWithRelativeTime)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    val sequence = MutableSequence.zero

    val steps1To9 = List(
      1 --> describe("DUT supports Binary Input Change with Relative Time."),
      2 --> PluginSteps.cyclePower,
      3 --> send(Requests.clearRestartIIN(sequence)) + expectNullResponse(sequence),
      4 --> PluginSteps.generateBinaryInputChangeEvents(),
      5 --> send(Requests.readAllObjects(sequence.increment(), Group2Var3)),
      (6 to 9) --> expectSingleResponse(
        sequence,
        AutoConfirm.Yes,
        ctoResponse(timeSync = false) &&
          PluginValidations.verifyLatestBinaryInputChangeEvents()
      )
    )

    val steps10To38 = if (options.get(TimeSyncOptions.supportSynchronizingTimeFromMaster)) {
      List(
        10 --> describe("Device supports time synchronization from DNP3 master"),
        11 --> delay(options.get(TimeSyncOptions.maxTimeFromResetToIIN14Ms)),
        12 --> send(AppFunction.delayMeas, sequence.increment(), List()),
        13 --> expectSingleResponse(sequence, isDelayMeasurement && Validations.containing(IIN.NEED_TIME)),
        14 --> writeCurrentTime(sequence.increment()),
        15 --> expectSingleResponse(sequence, Validations.isNull && Validations.without(IIN.NEED_TIME)),
        16 --> PluginSteps.generateBinaryInputChangeEvents(),
        17 --> send(Requests.readAllObjects(sequence.increment(), Group2Var3)),
        (18 to 21) --> expectSingleResponse(
          sequence,
          AutoConfirm.Yes,
          ctoResponse(timeSync = true) &&
            PluginValidations.verifyLatestBinaryInputChangeEvents()
        ),
        22 --> PluginSteps.generateBinaryInputChangeEvents(),
        23 --> delay(options.get(TimeSyncOptions.maxTimeBetweenSyncRequestsMs)),
        24 --> send(AppFunction.delayMeas, sequence.increment(), List()),
        25 --> expectSingleResponse(sequence, isDelayMeasurement && Validations.containing(IIN.NEED_TIME)),
        26 --> send(Requests.readAllObjects(sequence.increment(), Group2Var3)),
        (27 to 29) --> expectSingleResponse(
          sequence,
          AutoConfirm.No,
          ctoResponse(timeSync = true) &&
            // the response is left unconfirmed while steps 30-32 write the time, so a manual answer comes after
            PluginValidations.verifyLatestBinaryInputChangeEvents().deferrable
        ),
        30 --> describe("Withhold the application layer confirmation"),
        31 --> writeCurrentTime(sequence.increment()),
        32 --> expectSingleResponse(sequence, Validations.isNull && Validations.without(IIN.NEED_TIME)),
        33 --> send(Requests.readAllObjects(sequence.increment(), Group2Var3)),
        (34 to 36) --> expectSingleResponse(
          sequence,
          AutoConfirm.Yes,
          ctoResponse(timeSync = true) &&
            PluginValidations.verifyLatestBinaryInputChangeEvents()
        ),
        37 --> send(Requests.readAllObjects(sequence.increment(), Group2Var3)),
        38 --> expectNullResponse(sequence)
      )
    } else {
      List(
        (10 to 38) --> describe("Device does not support time synchronization from DNP3 master")
      )
    }

    steps1To9 ++ steps10To38
  }

  // g2v3 events after a CTO, unsynchronized (g51v2) before the time is set and synchronized (g51v1) after, in a
  // response that asks for confirmation, with flags as Note 5 requires
  private def ctoResponse(timeSync: Boolean): MessageValidation =
    eventsWithCtos(List(Group2Var3), Set(if (timeSync) Group51Var1 else Group51Var2)) && verifyFlagsComplyWithNote5 &&
      verifyConfirmRequested

}
