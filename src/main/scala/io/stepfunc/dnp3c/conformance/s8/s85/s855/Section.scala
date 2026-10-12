// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s85.s855

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.5.5"

  def description = "Multiple Object Request"

  override def procedures: Iterable[TestProcedure] = List(
    Procedure85521,
    Procedure85522
  )
}
