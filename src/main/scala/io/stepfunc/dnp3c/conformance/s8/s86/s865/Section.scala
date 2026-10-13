// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s86.s865

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.6.5"

  def description = "Broadcast Address and All Stations Indication"

  override def procedures: Iterable[TestProcedure] = List(
    TestProcedure("8.6.5.2", "Test Procedure", Procedure8652),
    TestProcedure("8.6.5.3", "Test Procedure", Procedure8653),
    TestProcedure("8.6.5.4", "Test Procedure, Confirmed Response Options", Procedure8654)
  )

}
