// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s86.s861

import io.stepfunc.dnp3c.conformance.api.{ TestProcedure, TestSection }

object Section extends TestSection {
  override def id: String = "8.6.1"
  override def description: String = "Restart"

  override def procedures: Iterable[TestProcedure] = List(
    Procedure8612
  )
}
