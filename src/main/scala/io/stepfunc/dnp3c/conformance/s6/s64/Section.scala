// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s6.s64

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {
  override def id = "6.4"
  override def description = "Test Retries"
  override def procedures: Iterable[TestProcedure] = List(TestProcedure("6.4.2", "Test Procedure", Procedure642))
}
