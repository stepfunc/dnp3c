// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s85.s852

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {
  def id = "8.5.2"

  def description = "Class 1"

  override def procedures: Iterable[TestProcedure] = List(
    Procedure85221,
    Procedure85222,
    Procedure85223,
    Procedure85224)
}
