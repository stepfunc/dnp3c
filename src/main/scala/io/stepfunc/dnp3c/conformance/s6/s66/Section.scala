// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s6.s66

import io.stepfunc.dnp3c.conformance.api.TestSection

object Section extends TestSection {

  override def id = "6.6"
  override def description = "Data Link Rejects Invalid Frames"
  override def sections: Iterable[TestSection] = List(s662.Section, s663.Section)

}
