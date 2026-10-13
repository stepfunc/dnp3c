// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s81

import io.stepfunc.dnp3c.conformance.api.{ TestProcedure, TestSection }

object Section extends TestSection {

  override def id = "8.1"
  override def description = "Binary Output Status"
  override def procedures: Iterable[TestProcedure] = List(TestProcedure("8.1.2", "Test Procedure", Procedure812))

}
