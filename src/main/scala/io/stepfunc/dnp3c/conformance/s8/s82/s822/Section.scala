// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s82.s822

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.2.2"

  def description = "Direct Operate with Acknowledge"

  override def procedures: Iterable[TestProcedure] = List(
    TestProcedure("8.2.2.2.1", "Binary Output, Direct Operate", Procedure82221),
    TestProcedure("8.2.2.2.2", "Binary Output, Direct Operate to Uninstalled Point", Procedure82222),
    TestProcedure("8.2.2.2.3", "Binary Output, Direct Operate, Configurable Device", Procedure82223)
  )
}
