// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.Validations._
import io.stepfunc.dnp3c.dnp3.Requests
import io.stepfunc.dnp3c.dnp3.app.{ EventClass, Sequence }

object Procedure8_5_5_2_2 extends ProcedureDefinition {

  override def steps(options: OptionSource): List[LabeledStep] = {
    List(
      1 --> send(Requests.readClass123(Sequence.zero)),
      (2 to 3) --> expectEventsEmptied(Sequence.zero),
      4 --> PluginSteps.generateClassEvents(EventClass.All),
      5 --> send(Requests.readClass1230(Sequence.one)),
      (6 to 13) --> expectAtLeastOneResponse(
        Sequence.one,
        PluginValidations.verifyLatestClassEvents(EventClass.All).acrossFragments &&
          PluginValidations.verifyAllStaticValues(isClass0 = true).acrossFragments &&
          verifyEventDataPrecedesStaticData &&
          verifyOnlyTable83OrTable84Contents(Tables(options)) &&
          verifyFlagsComplyWithFlagNote && verifyConfirmRequestedIfRequired
      )
    )
  }
}
