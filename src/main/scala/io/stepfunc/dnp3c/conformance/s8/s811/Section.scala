// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s811

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.11"

  def description = "Unsolicited Responses"

  override def sections: Iterable[TestSection] = List(
    section(
      "8.11.2",
      "Test Procedure",
      TestProcedure("8.11.2.1", "Unsolicited Response Configuration/Startup", Procedure81121),
      TestProcedure("8.11.2.2", "Unsolicited Response - Class 1 Data", Procedure81122),
      TestProcedure("8.11.2.3", "Unsolicited Response - Class 2 Data", Procedure81123),
      TestProcedure("8.11.2.4", "Unsolicited Response - Class 3 Data", Procedure81124),
      TestProcedure("8.11.2.5", "Unsolicited and Polled Responses", Procedure81125),
      TestProcedure("8.11.2.6", "Unsolicited Responses Off", Procedure81126)
    ))
}
