// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.Validations._
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group10Var0, Group10Var2 }
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.{ MessageValidation, Requests }

// a read of binary output status (g10); a device without binary outputs gives the answer `withoutOutputs` checks, which
// is what changed in 3.1r1
class BinaryOutputStatusRead(withoutOutputs: MessageValidation) extends ProcedureDefinition {

  override def steps(options: OptionSource): List[LabeledStep] = {

    def step1: LabeledStep = 1 --> send(Requests.readAllObjects(Sequence.zero, Group10Var0))

    def supported: LabeledStep = Label("steps: 2, 3 and 5") --> {
      describe("Device supports binary output status (g10)") +
        expectAtLeastOneResponse(
          Sequence.zero,
          isNotEmpty && verifyStaticResponse(Set(Group10Var2)) && verifyFlagsComplyWithFlagNote)
    }

    def unsupported: LabeledStep = 4 --> {
      describe("Device does NOT support binary output status (g10)") +
        expectSingleResponse(Sequence.zero, withoutOutputs)
    }

    if (options.get(SupportOptions.Outputs.binary.statusPoints)) List(step1, supported) else List(step1, unsupported)

  }
}

// 2.7: a Null Response, or an Error Response with IIN2.1
object Procedure8_1_2 extends BinaryOutputStatusRead(isNullOrErrorResponse(IIN.OBJECT_UNKNOWN))
