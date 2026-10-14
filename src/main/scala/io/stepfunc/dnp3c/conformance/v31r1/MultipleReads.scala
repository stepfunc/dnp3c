// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.{ Inputs, Outputs }
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.Validations._
import io.stepfunc.dnp3c.conformance.v27.MultipleObjectTypes
import io.stepfunc.dnp3c.dnp3.Requests
import io.stepfunc.dnp3c.dnp3.app.{ IIN, Sequence }

/**
 * 3.1r1's 8.20: a request for several object types gets events before static objects, in whatever order the types
 * come; 2.7 also wanted the static objects in the order requested. A subset level 1 device that can't read any of its
 * object types on their own answers such a request with an error instead.
 */
object MultipleReads {

  private val inputs = List(Inputs.binary, Inputs.doubleBit, Inputs.counter, Inputs.frozenCounter, Inputs.analog)

  // the DUT can read one of its object types on its own: an input's static points or events, or an output's status,
  // which no level lets it decline
  private val readsSome: Requirement = Requirement.anyOf(
    (inputs.flatMap(i =>
      List(
        Requirement.all(OptionCondition(i.points), OptionCondition(i.reads)),
        Requirement.all(OptionCondition(i.points), OptionCondition(i.events), OptionCondition(i.eventReads))
      )) ++
      List(OptionCondition(Outputs.binary.statusPoints), OptionCondition(Outputs.analog.statusPoints)))*)

  /** 8.20.2a: a request for each type the DUT has, which it can't read, gets a Null Error Response */
  val levelOne: ProcedureDefinition = new ProcedureDefinition {
    override protected def requirement: Requirement = Requirement.not(readsSome)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val (events, static) = MultipleObjectTypes.requested(options, onlyReadable = false)
      List(
        1 --> send(Requests.readHeaders(Sequence.zero, events ::: static)),
        2 --> expectSingleResponse(Sequence.zero, isNullErrorResponse(IIN.errors)),
        3 --> describe("No further 8.20 tests apply to this DUT")
      )
    }
  }

  /** 8.20.2b, of the types the DUT can read: a subset level 1 device needn't be able to read them all */
  val multipleObjectTypes: ProcedureDefinition =
    new MultipleObjectTypes(staticInRequestOrder = false, onlyReadable = true) {
      override protected def requirement: Requirement = readsSome
    }
}
