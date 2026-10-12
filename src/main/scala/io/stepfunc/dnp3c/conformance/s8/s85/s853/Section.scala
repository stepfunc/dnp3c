// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s85.s853

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {
  def id = "8.5.3"

  def description = "Class 2"

  override def procedures: Iterable[TestProcedure] = List(
    Procedure85321,
    Procedure85322,
    Procedure85323,
    Procedure85324)
}
