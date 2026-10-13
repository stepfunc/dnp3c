// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s82.s823

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.2.3"

  def description = "Direct Operate, No Acknowledge"

  override def procedures: Iterable[TestProcedure] = List(
    TestProcedure("8.2.3.2.1", "Binary Output, Direct Operate, No Acknowledge", Procedure82321),
    TestProcedure(
      "8.2.3.2.2",
      "Binary Output, Direct Operate, No Acknowledge, to an Uninstalled Point",
      Procedure82322),
    TestProcedure("8.2.3.2.3", "Binary Output, Direct Operate, No Acknowledge, Configurable Device", Procedure82323)
  )

}
