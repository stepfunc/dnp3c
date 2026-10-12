// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s822

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.22"

  def description = "Double-bit Input Change"

  override def sections: Iterable[TestSection] = List(
    section(
      "8.22.2",
      "Test Procedure",
      Procedure82221,
      Procedure82222,
      Procedure82223,
      Procedure82224,
      Procedure82225,
      Procedure82226,
      Procedure82227,
      Procedure82228,
      Procedure82229,
      Procedure822210,
      Procedure822211,
      Procedure822212,
      Procedure822213,
      Procedure822214,
      Procedure822215
    )
  )

}
