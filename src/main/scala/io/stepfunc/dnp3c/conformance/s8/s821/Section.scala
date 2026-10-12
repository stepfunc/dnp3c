// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s821

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.21"

  def description = "Double-bit Inputs"

  override def sections: Iterable[TestSection] = List(
    section(
      "8.21.2",
      "Test Procedure",
      Procedure82121,
      Procedure82122
    )
  )

}
