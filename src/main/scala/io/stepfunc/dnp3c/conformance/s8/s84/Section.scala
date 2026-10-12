// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s84

import io.stepfunc.dnp3c.conformance.api.TestSection

object Section extends TestSection {
  override def id = "8.4"
  override def description = "Analog Output Controls"
  override def sections: Iterable[TestSection] = List(s841.Section, s842.Section, s843.Section, s844.Section)
}
