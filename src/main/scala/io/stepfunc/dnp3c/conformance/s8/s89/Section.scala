// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s89

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.9"

  def description = "Application Layer Fragmentation"

  override def sections: Iterable[TestSection] = List(
    section(
      "8.9.1",
      "Use of FIR, FIN and SEQUENCE in Fragmentation",
      Procedure8912),
    section(
      "8.9.2",
      "Use of Confirmation in Fragmentation",
      Procedure8922)
  )

}
