// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s6.s65

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  override def id: String = "6.5"
  override def description: String = "DIR and FCV Bits"
  override def procedures: Iterable[TestProcedure] = List(Procedure652)

}
