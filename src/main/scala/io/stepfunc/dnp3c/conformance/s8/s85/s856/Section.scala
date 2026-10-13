// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s85.s856

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.5.6"

  def description = "Class Assignment Verification"

  override def procedures: Iterable[TestProcedure] = List(TestProcedure("8.5.6.2", "Test Procedure", Procedure8562))

}
