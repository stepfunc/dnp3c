// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s82.s823

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.2.3"

  def description = "Direct Operate, No Acknowledge"

  override def procedures: Iterable[TestProcedure] = List(
    Procedure82321,
    Procedure82322,
    Procedure82323
  )

}
