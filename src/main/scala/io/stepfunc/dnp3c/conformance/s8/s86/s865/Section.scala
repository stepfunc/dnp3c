// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s86.s865

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.6.5"

  def description = "Broadcast Address and All Stations Indication"

  override def procedures: Iterable[TestProcedure] = List(
    Procedure8652,
    Procedure8653,
    Procedure8654
  )

}
