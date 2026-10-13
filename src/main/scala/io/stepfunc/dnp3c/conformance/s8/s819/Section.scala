// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s819

import io.stepfunc.dnp3c.conformance.api.{ TestProcedure, TestSection, section }

object Section extends TestSection {

  def id = "8.19"

  def description = "Analog Change Event"

  override def sections: Iterable[TestSection] = List(
    section(
      "8.19.2",
      "Test Procedure",
      TestProcedure("8.19.2.1", "Analog Input Change", Procedure81921),
      TestProcedure("8.19.2.2", "Analog Input Change, Without Confirm", Procedure81922)
    )
  )
}
