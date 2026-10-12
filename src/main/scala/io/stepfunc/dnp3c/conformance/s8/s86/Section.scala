// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s86

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.6"

  def description = "Indications"

  override def sections: Iterable[TestSection] = List(
    s861.Section,
    s862.Section,
    s863.Section,
    s864.Section,
    s865.Section,
    s866.Section
  )

}
