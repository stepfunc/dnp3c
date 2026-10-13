// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s86.s863

import io.stepfunc.dnp3c.conformance.api.{ TestProcedure, TestSection }

object Section extends TestSection {
  override def id: String = "8.6.3"
  override def description: String = "Object Unknown"

  override def procedures: Iterable[TestProcedure] = List(TestProcedure("8.6.3.2", "Test Procedure", Procedure8632))
}
