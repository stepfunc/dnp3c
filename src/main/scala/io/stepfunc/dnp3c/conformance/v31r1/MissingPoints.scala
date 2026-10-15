// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api.{ OptionCondition, Requirement, TestStep }
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.InputOptions
import io.stepfunc.dnp3c.conformance.common.GenericSteps.expectSingleResponse
import io.stepfunc.dnp3c.conformance.common.Validations
import io.stepfunc.dnp3c.dnp3.MessageValidation
import io.stepfunc.dnp3c.dnp3.app.{ IIN, Sequence }

/**
 * How 3.1r1 has a DUT answer a read of a point type it has no points of, whether it never has them or they're
 * uninstalled or disabled for the test. Some of 3.1r1's steps still expect a Null Response there, but its Table 11-1,
 * and TB2016-001, which it follows, require an error. dnp3c applies the error to every point type alike, as the README's
 * known limitations explain. A DUT with the points but no events still answers a read of the events with a Null
 * Response.
 */
private[v31r1] object MissingPoints {

  /**
   * a Null Error Response: no objects, IIN2.1 or IIN2.2 set, and IIN2.0 clear; also the answer with which 8.24 and 8.25
   * let a level 1 or 2 device decline their first read
   */
  val answer: MessageValidation = Validations.isNullErrorResponse(IIN.objectErrors)

  def expect(seq: Sequence): TestStep = expectSingleResponse(seq, answer)

  /** a test of a type's events for a DUT without them, which needs the events to be readable unless it has no points */
  def withoutEvents(input: InputOptions): Requirement =
    Requirement.all(input.withoutEvents, Requirement.anyOf(!input.points, OptionCondition(input.eventReads)))
}
