// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s86.s864

import io.stepfunc.dnp3c.conformance.api.options.SupportOptions
import io.stepfunc.dnp3c.conformance.api.{ LabeledStep, OptionSource, TestProcedure, _ }
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3.Requests
import io.stepfunc.dnp3c.dnp3.app.objects.Group10Var0
import io.stepfunc.dnp3c.dnp3.app.{ IIN, Sequence }

object Procedure8642 extends TestProcedure {

  override def id: String = "8.6.4.2"

  override def description: String = "Test Procedure for Devices with Global Remote Supervisory Control"

  override protected def requiredOptions = List(SupportOptions.globalRemoteSupervisoryControl)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    List(
      1 --> describe(
        "This section needs a DUT whose binary outputs can be switched to local control."
      ),
      2 --> PluginSteps.enableRemoteSupervisoryControl(),
      3 --> send(Requests.readAllObjects(Sequence.zero, Group10Var0)),
      4 --> expectAtLeastOneResponse(Sequence.zero, without(IIN.LOCAL_CONTROL) && verifyConfirmRequestedIfRequired),
      5 --> PluginSteps.setGlobalRemoteSupervisoryControl(),
      6 --> send(Requests.readAllObjects(Sequence.one, Group10Var0)),
      7 --> expectAtLeastOneResponse(Sequence.one, containing(IIN.LOCAL_CONTROL) && verifyConfirmRequestedIfRequired)
    )
  }
}
