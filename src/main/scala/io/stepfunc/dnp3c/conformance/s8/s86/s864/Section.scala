// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s86.s864

import io.stepfunc.dnp3c.conformance.api.{ TestProcedure, TestSection }

object Section extends TestSection {
  override def id: String = "8.6.4"
  override def description: String = "Local"

  override def procedures: Iterable[TestProcedure] = List(
    TestProcedure("8.6.4.2", "Test Procedure for Devices with Global Remote Supervisory Control", Procedure8642),
    TestProcedure("8.6.4.3", "Test Procedure for Devices with Individual Remote Supervisory Control", Procedure8643)
  )
}
