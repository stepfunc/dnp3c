// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.Validations._
import io.stepfunc.dnp3c.dnp3.{ AutoConfirm, Requests }
import io.stepfunc.dnp3c.dnp3.app.{ EventClass, Sequence }

object Procedure8_5_5_2_1 extends ProcedureDefinition {

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
            verifyOnlyTable84Contents(Tables(options)))
        )
    )
  }
}
