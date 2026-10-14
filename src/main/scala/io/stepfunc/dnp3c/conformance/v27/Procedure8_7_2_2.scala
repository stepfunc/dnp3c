// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api.options.{ SupportOptions, TimeSyncOptions }
import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.Validations._
import io.stepfunc.dnp3c.dnp3.app.{ IIN, PointType, Sequence }
import io.stepfunc.dnp3c.dnp3.{ MessageValidation, Requests }

object Procedure8_7_2_2 extends ProcedureDefinition {

  // a device that never sets IIN1-4 need not support section 8.7
  override def requiredOptions = List(TimeSyncOptions.supportSynchronizingTimeFromMaster)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {

    val waitMs = options.get(TimeSyncOptions.maxTimeFromResetToIIN14Ms)
    val latencyMs = options.get(TimeSyncOptions.timeWriteLatencyMs)
    val tables = Tables(options)

    List(
      1 --> PluginSteps.cyclePower,
      2 --> delay(waitMs),
      3 --> send(Requests.readClass1(Sequence.zero)),
      4 --> expectAtLeastOneResponse(Sequence.zero, containing(IIN.NEED_TIME) && verifyConfirmRequestedIfRequired),
      5 --> writeCurrentTime(Sequence.one, latencyMs),
      6 --> expectSingleResponse(Sequence.one, isNull && without(IIN.NEED_TIME)),
      7 --> PluginSteps.generateTimeEvent(),
      8 --> send(Requests.readClass123(Sequence.two)),
      // step 4 only emptied class 1, so class 2 and 3 events pending since the restart can make this several fragments
      9 --> expectAtLeastOneResponse(
        Sequence.two,
        verifyOnlyTable84Contents(tables) && verifyConfirmRequestedIfRequired &&
          (timeTaggedEvent && PluginValidations.verifyTimestamp()).acrossFragments
      )
    )
  }

  private[conformance] def timeTaggedEvent: MessageValidation =
    MessageValidation.check("Verify that the response holds an event that carries its time") { message =>
      if (!message.measurements(PointType.Event).exists(_.point.timestamp.isDefined))
        throw new Exception("Expected an event with a time in the response")
    }

}
