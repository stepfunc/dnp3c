// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s812

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.12"

  def description = "Collision Avoidance"

  override def procedures: Iterable[TestProcedure] = List(
    unimplemented("8.12.2", "Test Procedure"))

}
