// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s822

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s8.helpers.InputEventProcedures.NoConfirm
import io.stepfunc.dnp3c.conformance.s8.helpers.InputType

object Procedure82225 extends NoConfirm(InputType.DoubleBitBinaryInput) {

  override protected def notes: List[LabeledStep] = List(
    Label.note --> describe(
      "These steps follow 8.14.2.5. The document's step 11, a check for remaining events, has no " +
        "request before it, so steps 11 to 20 below are the document's steps 12 to 21"))
}
