// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s816

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.16"

  def description = "Binary Counters"

  override def sections: Iterable[TestSection] = List(
    section(
      "8.16.1",
      "Binary Counters, Running",
      Procedure816121,
      Procedure816122,
      Procedure816123),
    section(
      "8.16.2",
      "Binary Counters, Frozen",
      Procedure816221,
      Procedure816222,
      Procedure816223,
      Procedure816224,
      Procedure816225)
  )
}
