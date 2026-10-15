// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.dnp3.app.{ Sequence, Variation }
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group3Var0, Group4Var0 }
import io.stepfunc.dnp3c.dnp3.{ Comment, DeferredCheck, PointKind, Requests, TestDriver }

/**
 * 3.1r1's 8.21.1 and 8.22.1: a device with double-bit inputs is configured to disable them, failing if it can't be;
 * then a read of them, static (8.21.1) or events (8.22.1), gets a Null Error Response with IIN2.1 or IIN2.2.
 * Afterwards the points are restored, so the tests after this one find the device as before.
 */
final class DisableDoubleBit(read: Variation) extends ProcedureDefinition {

  override protected def plan(options: OptionSource): Plan = {
    val points = options.get(Inputs.doubleBit.points)
    val disabling = options.get(Inputs.doubleBit.disabling)
    val removal = PluginSteps.removal(PointKind.DoubleBitInput)
    val disable: TestStep =
      if (!points) describe("The DUT has no double-bit inputs: there's nothing to disable")
      else if (disabling) removal.apply
      else
        new TestStep {
          def description: List[Comment] = List(Comment("The DUT must be able to disable its double-bit inputs"))
          def run(driver: TestDriver): List[DeferredCheck] = throw new Exception(
            s"The DUT has double-bit inputs it can't be configured to disable (${Inputs.doubleBit.disabling.id} is " +
              "false), which 3.1r1 counts as a failure")
        }
    Plan(
      List(
        1 --> disable,
        2 --> send(Requests.readAllObjects(Sequence.one, read)),
        3 --> MissingPoints.expect(Sequence.one)
      ),
      if (points && disabling) List(Label("restore") --> removal.restore) else Nil
    )
  }
}

object DisableDoubleBit {

  /** 8.21.1: a read of g3 gets a Null Error Response with IIN2.1 or IIN2.2 */
  val static = new DisableDoubleBit(Group3Var0)

  /**
   * 8.22.1: a read of g4 gets a Null Error Response with IIN2.1 or IIN2.2, as 8.21.1's does; 3.1r1's step makes the error
   * optional (see MissingPoints)
   */
  val events = new DisableDoubleBit(Group4Var0)
}
