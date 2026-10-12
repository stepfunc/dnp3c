// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s82.s822

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.2.2"

  def description = "Direct Operate with Acknowledge"

  override def procedures: Iterable[TestProcedure] = List(
    Procedure82221,
    Procedure82222,
    Procedure82223
  )
}
