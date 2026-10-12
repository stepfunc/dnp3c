// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s82.s825

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {
  def id = "8.2.5"

  def description = "Control Code Support"

  override def procedures: Iterable[TestProcedure] = List(
    Procedure82521,
    Procedure82522
  )
}
