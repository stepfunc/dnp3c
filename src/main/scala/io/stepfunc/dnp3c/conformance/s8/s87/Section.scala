// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s87

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {
  def id = "8.7"

  def description = "Time"

  override def sections: Iterable[TestSection] = List(s871.Section, s872.Section)
}
