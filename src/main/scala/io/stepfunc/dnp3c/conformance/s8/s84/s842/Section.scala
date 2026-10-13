// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s84.s842

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {
  def id = "8.4.2"

  def description = "Analog Output Controls, Direct Operate"

  override def procedures: Iterable[TestProcedure] = List(
    TestProcedure("8.4.2.2.1", "Analog Output, Direct Operate", Procedure84221),
    TestProcedure("8.4.2.2.2", "Analog Output, Direct Operate, to Uninstalled Point", Procedure84222),
    TestProcedure("8.4.2.2.3", "Analog Output, Direct Operate, Configurable Device", Procedure84223)
  )
}
