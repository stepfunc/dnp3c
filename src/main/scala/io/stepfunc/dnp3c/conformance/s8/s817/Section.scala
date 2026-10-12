// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s817

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.17"

  def description = "Binary Counters, Event"

  override def sections: Iterable[TestSection] = List(
    section(
      "8.17.2",
      "Test Procedure",
      Procedure81721,
      Procedure81722,
      Procedure81723,
      Procedure81724,
      Procedure81725))
}
