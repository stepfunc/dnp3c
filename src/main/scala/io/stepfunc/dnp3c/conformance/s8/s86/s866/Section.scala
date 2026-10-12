// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s86.s866

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.6.6"

  def description = "Buffer Overflow"

  override def procedures: Iterable[TestProcedure] = List(
    Procedure86621,
    Procedure86622,
    Procedure86623,
    Procedure86624
  )

}
