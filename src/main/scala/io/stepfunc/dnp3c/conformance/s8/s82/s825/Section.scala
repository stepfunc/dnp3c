// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s82.s825

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {
  def id = "8.2.5"

  def description = "Control Code Support"

  override def procedures: Iterable[TestProcedure] = List(
    TestProcedure("8.2.5.2.1", "Binary Output, Complementary Function Support", Procedure82521),
    TestProcedure("8.2.5.2.2", "Binary Output, Single Function Support", Procedure82522)
  )
}
