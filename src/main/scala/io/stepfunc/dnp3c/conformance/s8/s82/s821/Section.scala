// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s82.s821

import io.stepfunc.dnp3c.conformance.api.{ TestProcedure, TestSection }

object Section extends TestSection {

  def id = "8.2.1"

  def description = "Select Before Operate"

  override def procedures: Iterable[TestProcedure] = List(
    Procedure82121,
    Procedure82122,
    Procedure82123,
    Procedure82124,
    Procedure82125,
    Procedure82126,
    Procedure82127,
    Procedure82128,
    Procedure82129,
    Procedure821210,
    Procedure821211,
    Procedure821212,
    Procedure821213,
    Procedure821214,
    Procedure821215
  )

}
