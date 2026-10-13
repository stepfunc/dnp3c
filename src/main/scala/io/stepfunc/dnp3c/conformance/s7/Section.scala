// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s7

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  override def id = "7"
  override def description = "Transport Layer"
  override def procedures: Iterable[TestProcedure] =
    List(TestProcedure("7.2", "Segmentation of application layer class 0 poll", Procedure72))

}
