// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s6.s63

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  override def id = "6.3"
  override def description = "Request Link Status"
  override def procedures: Iterable[TestProcedure] = List(TestProcedure("6.3.2", "Test Procedure", Procedure632))
}
