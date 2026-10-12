// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s87.s871

import io.stepfunc.dnp3c.conformance.api.{ TestProcedure, TestSection }

object Section extends TestSection {
  override def id: String = "8.7.1"
  override def description: String = "Delay Measurement"

  override def procedures: Iterable[TestProcedure] = List(Procedure8712)
}
