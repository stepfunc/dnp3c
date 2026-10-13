// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s85.s852

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {
  def id = "8.5.2"

  def description = "Class 1"

  override def procedures: Iterable[TestProcedure] = List(
    TestProcedure("8.5.2.2.1", "Class 1 Data, 0x06", Procedure85221),
    TestProcedure("8.5.2.2.2", "Class 1 Data, 0x07", Procedure85222),
    TestProcedure("8.5.2.2.3", "Class 1 Data, 0x08", Procedure85223),
    TestProcedure("8.5.2.2.4", "Class 1 Data Without Confirm", Procedure85224)
  )
}
