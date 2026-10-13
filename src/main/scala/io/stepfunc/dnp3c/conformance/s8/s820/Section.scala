// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s820

import io.stepfunc.dnp3c.conformance.api.{ TestProcedure, TestSection }

object Section extends TestSection {

  def id = "8.20"

  def description = "Multiple Read Requests"

  override def procedures: List[TestProcedure] = List(TestProcedure("8.20.2", "Test Procedure", Procedure8202))

}
