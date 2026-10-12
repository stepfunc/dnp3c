// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s811

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.11"

  def description = "Unsolicited Responses"

  override def sections: Iterable[TestSection] = List(
    section(
      "8.11.2",
      "Test Procedure",
      Procedure81121,
      Procedure81122,
      Procedure81123,
      Procedure81124,
      Procedure81125,
      Procedure81126))
}
