// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s6.s61

import io.stepfunc.dnp3c.conformance.api.{ TestProcedure, TestSection }

object Section extends TestSection {

  def id = "6.1"
  def description = "Reset link and passive confirm support"
  override def procedures: Iterable[TestProcedure] = List(TestProcedure("6.1.2", "Test Procedure", s612.Procedure612))

}
