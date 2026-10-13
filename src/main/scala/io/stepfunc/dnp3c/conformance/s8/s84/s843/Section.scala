// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s84.s843

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {
  def id = "8.4.3"

  def description = "Analog Output Controls, Direct Operate No Acknowledgment"

  override def procedures: Iterable[TestProcedure] = List(
    TestProcedure("8.4.3.2.1", "Analog Output, Direct Operate, No Acknowledge", Procedure84321),
    TestProcedure(
      "8.4.3.2.2",
      "Analog Output, Direct Operate, No Acknowledge, to an Uninstalled Point",
      Procedure84322),
    TestProcedure("8.4.3.2.3", "Analog Output, Direct Operate, No Acknowledge, Configurable Device", Procedure84323)
  )

}
