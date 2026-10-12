// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s815

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.15"

  def description = "Common Time of Occurrence"

  override def procedures: Iterable[TestProcedure] = List(Procedure8152)
}
