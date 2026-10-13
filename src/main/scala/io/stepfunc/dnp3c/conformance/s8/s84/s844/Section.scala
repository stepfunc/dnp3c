// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s84.s844

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {
  def id = "8.4.4"

  def description = "Analog Output Controls, Multiple Objects"

  override def procedures: Iterable[TestProcedure] = List(TestProcedure("8.4.4.2", "Test Procedure", Procedure8442))
}
