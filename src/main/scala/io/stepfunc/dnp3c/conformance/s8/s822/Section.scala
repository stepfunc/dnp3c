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
      TestProcedure("8.22.2.1", "Device does not support Double-bit Input Changes", Procedure82221),
      TestProcedure("8.22.2.2", "Double-bit Input Change, 0x06", Procedure82222),
      TestProcedure("8.22.2.3", "Double-bit Input Change, 0x07", Procedure82223),
      TestProcedure("8.22.2.4", "Double-bit Input Change, 0x08", Procedure82224),
      TestProcedure("8.22.2.5", "Double-bit Input Change Without Confirm", Procedure82225),
      TestProcedure("8.22.2.6", "Double-bit Input Change Without Time, 0x06", Procedure82226),
      TestProcedure("8.22.2.7", "Double-bit Input Change Without Time, 0x07", Procedure82227),
      TestProcedure("8.22.2.8", "Double-bit Input Change Without Time, 0x08", Procedure82228),
      TestProcedure("8.22.2.9", "Double-bit Input Change With Time, 0x06", Procedure82229),
      TestProcedure("8.22.2.10", "Double-bit Input Change With Time, 0x07", Procedure822210),
      TestProcedure("8.22.2.11", "Double-bit Input Change With Time, 0x08", Procedure822211),
      TestProcedure("8.22.2.12", "Double-bit Input Change With Relative Time, 0x06", Procedure822212),
      TestProcedure("8.22.2.13", "Double-bit Input Change With Relative Time, 0x07", Procedure822213),
      TestProcedure("8.22.2.14", "Double-bit Input Change With Relative Time, 0x08", Procedure822214),
      TestProcedure("8.22.2.15", "Double-bit Input Change with Relative Time, Long Interval", Procedure822215)
    )
  )

}
