// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s813

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.13"

  def description = "Binary Inputs"

  override def procedures: Iterable[TestProcedure] = List(
    TestProcedure("8.13.2.1", "Device does not have Binary Inputs", Procedure81321),
    TestProcedure("8.13.2.2", "Device has Binary Inputs", Procedure81322),
    TestProcedure("8.13.2.3", "Device supports Binary Inputs, Configurable Device", Procedure81323)
  )

}
