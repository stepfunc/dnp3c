// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s816

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.16"

  def description = "Binary Counters"

  override def sections: Iterable[TestSection] = List(
    section(
      "8.16.1",
      "Binary Counters, Running",
      TestProcedure("8.16.1.2.1", "Device does not have Binary Counters", Procedure816121),
      TestProcedure("8.16.1.2.2", "Device responds with Binary Counters", Procedure816122),
      TestProcedure("8.16.1.2.3", "Device supports Binary Counters, Configurable Device", Procedure816123)
    ),
    section(
      "8.16.2",
      "Binary Counters, Frozen",
      TestProcedure("8.16.2.2.1", "Device does not have Frozen Counters", Procedure816221),
      TestProcedure("8.16.2.2.2", "Binary Counter, Freeze", Procedure816222),
      TestProcedure("8.16.2.2.3", "Binary Counter, Freeze And Clear", Procedure816223),
      TestProcedure("8.16.2.2.4", "Binary Counter, Freeze, No Acknowledge", Procedure816224),
      TestProcedure("8.16.2.2.5", "Binary Counter, Freeze And Clear, No Acknowledge", Procedure816225)
    )
  )
}
