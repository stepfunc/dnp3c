// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s813

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.13"

  def description = "Binary Inputs"

  override def procedures: Iterable[TestProcedure] = List(
    Procedure81321,
    Procedure81322,
    Procedure81323)

}
