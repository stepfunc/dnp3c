// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s84.s841

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.4.1"

  def description = "Analog Output Controls, Select Before Operate"

  override def procedures: Iterable[TestProcedure] = List(
    Procedure84121,
    Procedure84122,
    Procedure84123,
    Procedure84124,
    Procedure84125,
    Procedure84126,
    Procedure84127,
    Procedure84128,
    Procedure84129,
    Procedure841210,
    Procedure841211,
    Procedure841212
  )
}
