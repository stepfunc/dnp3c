// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s817

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.17"

  def description = "Binary Counters, Event"

  override def sections: Iterable[TestSection] = List(
    section(
      "8.17.2",
      "Test Procedure",
      TestProcedure("8.17.2.1", "Device does not support Binary Counter Events", Procedure81721),
      TestProcedure("8.17.2.2", "Binary Counters, Event, 0x06", Procedure81722),
      TestProcedure("8.17.2.3", "Binary Counters, Event, 0x07", Procedure81723),
      TestProcedure("8.17.2.4", "Binary Counters, Event, 0x08", Procedure81724),
      TestProcedure("8.17.2.5", "Binary Counters, Event Without Confirm", Procedure81725)
    ))
}
