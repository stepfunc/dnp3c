// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s84.s842

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {
  def id = "8.4.2"

  def description = "Analog Output Controls, Direct Operate"

  override def procedures: Iterable[TestProcedure] = List(
    Procedure84221,
    Procedure84222,
    Procedure84223
  )
}
