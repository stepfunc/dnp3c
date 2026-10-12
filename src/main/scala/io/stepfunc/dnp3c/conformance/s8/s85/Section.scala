// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s85

import io.stepfunc.dnp3c.conformance.api.TestSection

object Section extends TestSection {

  def id = "8.5"

  def description = "Class Data"

  override def sections: Iterable[TestSection] = List(
    s851.Section,
    s852.Section,
    s853.Section,
    s854.Section,
    s855.Section,
    s856.Section)

}
