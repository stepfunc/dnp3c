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
      Procedure82321,
      Procedure82322,
      Procedure82323,
      Procedure82324
    )
  )

}
