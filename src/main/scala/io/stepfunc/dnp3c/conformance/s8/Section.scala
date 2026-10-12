// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8

import io.stepfunc.dnp3c.conformance.api.TestSection

object Section extends TestSection {

  override def id = "8"

  override def description = "Application Layer"

  override def sections: Iterable[TestSection] = List(
    s81.Section,
    s82.Section,
    s83.Section,
    s84.Section,
    s85.Section,
    s86.Section,
    s87.Section,
    s88.Section,
    s89.Section,
    s810.Section,
    s811.Section,
    s812.Section,
    s813.Section,
    s814.Section,
    s815.Section,
    s816.Section,
    s817.Section,
    s818.Section,
    s819.Section,
    s820.Section,
    s821.Section,
    s822.Section,
    s823.Section
  )

}
