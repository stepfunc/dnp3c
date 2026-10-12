// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s85.s854

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {
  def id = "8.5.4"

  def description = "Class 3"

  override def procedures: Iterable[TestProcedure] = List(
    Procedure85421,
    Procedure85422,
    Procedure85423,
    Procedure85424)
}
