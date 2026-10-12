// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s85.s855

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3.{ AutoConfirm, Requests }
import io.stepfunc.dnp3c.dnp3.app.{ EventClass, Sequence }

object Procedure85521 extends TestProcedure {

  override def id = "8.5.5.2.1"

  override def description = "Multiple Object Request, Class 1, 2, and 3"

  override def steps(options: OptionSource): List[LabeledStep] = {
    List(
      1 --> send(Requests.readClass123(Sequence.zero)),
      (2 to 3) --> expectEventsEmptied(Sequence.zero),
      4 --> PluginSteps.generateClassEvents(EventClass.All),
      5 --> send(Requests.readClass123(Sequence.one)),
      (6 to 11) -->
        expectSingleResponse(
          Sequence.one,
          AutoConfirm.Yes,
          eventResponse(
            PluginValidations.verifyLatestClassEvents(EventClass.All),
            verifyOnlyTable84Contents(options.get(SupportOptions.Inputs.doubleBit.points)))
        )
    )
  }
}
