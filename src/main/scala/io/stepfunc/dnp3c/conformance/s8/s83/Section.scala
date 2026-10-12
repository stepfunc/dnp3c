// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s83

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.3"

  def description = "Analog Output Status"

  override def procedures: Iterable[TestProcedure] = List(Procedure832)
}
