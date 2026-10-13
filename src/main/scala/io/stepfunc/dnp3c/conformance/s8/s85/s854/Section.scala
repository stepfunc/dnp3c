// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s85.s854

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {
  def id = "8.5.4"

  def description = "Class 3"

  override def procedures: Iterable[TestProcedure] = List(
    TestProcedure("8.5.4.2.1", "Class 3 Data, 0x06", Procedure85421),
    TestProcedure("8.5.4.2.2", "Class 3 Data, 0x07", Procedure85422),
    TestProcedure("8.5.4.2.3", "Class 3 Data, 0x08", Procedure85423),
    TestProcedure("8.5.4.2.4", "Class 3 Data Without Confirm", Procedure85424)
  )
}
