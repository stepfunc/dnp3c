// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s6.s67

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "6.7"

  def description = "Self-Address Support"

  override def procedures: Iterable[TestProcedure] = List(Procedure672)

}
