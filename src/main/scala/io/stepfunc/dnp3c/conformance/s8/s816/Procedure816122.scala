// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s816

import io.stepfunc.dnp3c.conformance.api.options.SupportOptions._
import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.app.objects.Group20Var0
import io.stepfunc.dnp3c.dnp3.Requests

object Procedure816122 extends TestProcedure {

  override def id: String = "8.16.1.2.2"

  override def description: String = "Device responds with Binary Counters"

  override def requiredOptions = List(Inputs.counter.points)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    // counter objects from Table 8-5, and at least one of them
    val counters = Validations.verifyOnlyTable85Contents && Validations.isNotEmpty.acrossFragments

    List(
      1 --> send(Requests.readAllObjects(Sequence.one, Group20Var0)),
      2 --> expectAtLeastOneResponse(Sequence.one, counters),
      3 --> PluginSteps.recordCurrentCounterValues(),
      4 --> PluginSteps.generateCounterInputData(),
      5 --> send(Requests.readAllObjects(Sequence.two, Group20Var0)),
      (6 to 8) --> expectAtLeastOneResponse(
        Sequence.two,
        PluginValidations.verifyAllCountersCurrentValues().acrossFragments && counters &&
          Validations.verifyFlagsComplyWithNote5
      )
    )
  }
}
