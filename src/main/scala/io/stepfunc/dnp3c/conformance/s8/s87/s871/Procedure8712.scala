// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s87.s871

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.TimeSyncOptions
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations
import io.stepfunc.dnp3c.dnp3.app.{ AppFunction, Sequence }

object Procedure8712 extends TestProcedure {

  override def id: String = "8.7.1.2"

  override def description: String = "Test Procedure"

  // a device that never sets IIN1-4 need not support section 8.7
  override def requiredOptions = List(TimeSyncOptions.supportSynchronizingTimeFromMaster)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    def expectDelay(seq: Sequence): TestStep =
      expectSingleResponse(seq, Validations.isDelayMeasurement && PluginValidations.verifyDelayMeasurementAccuracy())

    List(
      1 --> send(AppFunction.delayMeas, Sequence.zero),
      (2 to 3) --> expectDelay(Sequence.zero),
      4 --> describe("Measure the delay two more times") +
        send(AppFunction.delayMeas, Sequence.one) + expectDelay(Sequence.one) +
        send(AppFunction.delayMeas, Sequence.two) + expectDelay(Sequence.two)
    )
  }
}
