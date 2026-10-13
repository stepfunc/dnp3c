// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s87.s872

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {
  override def id: String = "8.7.2"
  override def description: String = "Synchronization"

  override def procedures: Iterable[TestProcedure] = List(TestProcedure("8.7.2.2", "Test Procedure", Procedure8722))
}
