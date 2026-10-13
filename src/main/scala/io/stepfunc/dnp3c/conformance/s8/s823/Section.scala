// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s823

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.23"

  def description = "Device Attributes"

  override def sections: Iterable[TestSection] = List(
    section(
      "8.23.2",
      "Test Procedure",
      TestProcedure("8.23.2.1", "Variation 255 – list of attribute variations", Procedure82321),
      TestProcedure("8.23.2.2", "Variation 254 – non-specific all attributes request", Procedure82322),
      TestProcedure("8.23.2.3", "Variation nnn – specific attributes read request", Procedure82323),
      TestProcedure("8.23.2.4", "Variation nnn – specific attributes write request", Procedure82324)
    )
  )

}
