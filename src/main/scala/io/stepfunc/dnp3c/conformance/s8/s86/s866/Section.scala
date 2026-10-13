// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s86.s866

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.6.6"

  def description = "Buffer Overflow"

  override def procedures: Iterable[TestProcedure] = List(
    TestProcedure("8.6.6.2.1", "Buffer Overflow, Binary Input Change Event Buffers", Procedure86621),
    TestProcedure("8.6.6.2.2", "Buffer Overflow, Analog Input Change Event Buffers", Procedure86622),
    TestProcedure("8.6.6.2.3", "Buffer Overflow, Binary Counter Change Event Buffers", Procedure86623),
    TestProcedure("8.6.6.2.4", "Double-bit Input Change Event Buffers", Procedure86624)
  )

}
