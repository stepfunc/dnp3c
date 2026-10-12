// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s84.s843

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {
  def id = "8.4.3"

  def description = "Analog Output Controls, Direct Operate No Acknowledgment"

  override def procedures: Iterable[TestProcedure] = List(
    Procedure84321,
    Procedure84322,
    Procedure84323)

}
