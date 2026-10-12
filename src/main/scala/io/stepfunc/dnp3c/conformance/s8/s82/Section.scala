// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s82

import io.stepfunc.dnp3c.conformance.api.TestSection

object Section extends TestSection {

  override def id = "8.2"
  override def description = "Binary Output Controls"

  override def sections: Iterable[TestSection] = List(
    s821.Section,
    s822.Section,
    s823.Section,
    s824.Section,
    s825.Section)
}
