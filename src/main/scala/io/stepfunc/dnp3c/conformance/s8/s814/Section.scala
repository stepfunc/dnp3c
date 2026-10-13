// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s814

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.14"

  def description = "Binary Input Change"

  override def procedures: Iterable[TestProcedure] = List(
    TestProcedure("8.14.2.1", "Device does not support Binary Input Changes", Procedure81421),
    TestProcedure("8.14.2.2", "Binary Input Change, 0x06", Procedure81422),
    TestProcedure("8.14.2.3", "Binary Input Change, 0x07", Procedure81423),
    TestProcedure("8.14.2.4", "Binary Input Change, 0x08", Procedure81424),
    TestProcedure("8.14.2.5", "Binary Input Change Without Confirm", Procedure81425),
    TestProcedure("8.14.2.6", "Binary Input Change Without Time, 0x06", Procedure81426),
    TestProcedure("8.14.2.7", "Binary Input Change Without Time, 0x07", Procedure81427),
    TestProcedure("8.14.2.8", "Binary Input Change Without Time, 0x08", Procedure81428),
    TestProcedure("8.14.2.9", "Binary Input Change With Time, 0x06", Procedure81429),
    TestProcedure("8.14.2.10", "Binary Input Change With Time, 0x07", Procedure814210),
    TestProcedure("8.14.2.11", "Binary Input Change With Time, 0x08", Procedure814211),
    TestProcedure("8.14.2.12", "Binary Input Change With Relative Time, 0x06", Procedure814212),
    TestProcedure("8.14.2.13", "Binary Input Change With Relative Time, 0x07", Procedure814213),
    TestProcedure("8.14.2.14", "Binary Input Change With Relative Time, 0x08", Procedure814214),
    TestProcedure("8.14.2.15", "Binary Input Change with Relative Time, Long Interval", Procedure814215)
  )
}
