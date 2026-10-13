// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s85.s851

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {
  def id = "8.5.1"

  def description = "Class 0"

  override def procedures: Iterable[TestProcedure] = List(
    TestProcedure("8.5.1.2", "Test Procedure", Procedure8512)
  )
}
