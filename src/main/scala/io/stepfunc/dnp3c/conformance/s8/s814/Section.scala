// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s814

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.14"

  def description = "Binary Input Change"

  override def procedures: Iterable[TestProcedure] = List(
    Procedure81421,
    Procedure81422,
    Procedure81423,
    Procedure81424,
    Procedure81425,
    Procedure81426,
    Procedure81427,
    Procedure81428,
    Procedure81429,
    Procedure814210,
    Procedure814211,
    Procedure814212,
    Procedure814213,
    Procedure814214,
    Procedure814215
  )
}
