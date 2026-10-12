// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s818

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.18"

  def description = "Analog Input"

  override def sections: Iterable[TestSection] = List(
    section(
      "8.18.2",
      "Test Procedure",
      Procedure81821,
      Procedure81822)
  )

}
