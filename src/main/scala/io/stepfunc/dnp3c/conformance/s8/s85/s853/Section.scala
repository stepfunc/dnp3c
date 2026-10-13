// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s85.s853

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {
  def id = "8.5.3"

  def description = "Class 2"

  override def procedures: Iterable[TestProcedure] = List(
    TestProcedure("8.5.3.2.1", "Class 2 Data, 0x06", Procedure85321),
    TestProcedure("8.5.3.2.2", "Class 2 Data, 0x07", Procedure85322),
    TestProcedure("8.5.3.2.3", "Class 2 Data, 0x08", Procedure85323),
    TestProcedure("8.5.3.2.4", "Class 2 Data Without Confirm", Procedure85324)
  )
}
