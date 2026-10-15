// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.common

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.dnp3.{ MessageValidation, PointKind, Requests }
import io.stepfunc.dnp3c.dnp3.app.{ IIN, Sequence, Variation }

// 8.13.2.3, 8.16.1.2.3 and 8.21.2.2: with the DUT's points of a type uninstalled or disabled, a read of them gets a
// null response or IIN2-1; a procedure object gives its required options, and its section gives it a number and title
// `withoutPoints` is the answer expected with them gone: 2.7 takes a Null Response or IIN2-1
abstract class ConfigurableInputProcedure(
    points: String,
    kind: PointKind,
    variation: Variation,
    seq: Sequence,
    withoutPoints: MessageValidation = Validations.isNullOrErrorResponse(IIN.OBJECT_UNKNOWN))
    extends ProcedureDefinition {
  override protected def plan(options: OptionSource): Plan = {
    val removal = PluginSteps.removal(kind)
    Plan(
      List(
        1 --> describe(s"The DUT's $points can be uninstalled or disabled through its configuration"),
        2 --> removal.apply,
        3 --> send(Requests.readAllObjects(seq, variation)),
        4 --> expectSingleResponse(seq, withoutPoints)
      ),
      List(Label("restore") --> removal.restore)
    )
  }
}
