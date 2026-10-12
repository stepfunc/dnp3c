// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s6

import io.stepfunc.dnp3c.conformance.api.{ TestProcedure, TestSection }

object Section extends TestSection {

  def id: String = "6"

  def description: String = "Link Layer"

  override def procedures: Iterable[TestProcedure] = Nil

  override def sections: Iterable[TestSection] = List(
    s61.Section,
    s63.Section,
    s64.Section,
    s65.Section,
    s66.Section,
    s67.Section)

}
