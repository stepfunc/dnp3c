// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s84.s841

import io.stepfunc.dnp3c.conformance.api._

object Section extends TestSection {

  def id = "8.4.1"

  def description = "Analog Output Controls, Select Before Operate"

  override def procedures: Iterable[TestProcedure] = List(
    TestProcedure("8.4.1.2.1", "Analog Output, SBO, 0x28", Procedure84121),
    TestProcedure("8.4.1.2.2", "Analog Output, SBO, 0x17", Procedure84122),
    TestProcedure("8.4.1.2.3", "Analog Output, SBO, to Uninstalled Point", Procedure84123),
    TestProcedure("8.4.1.2.4", "Analog Output, SBO, Execute Issued After Time-out", Procedure84124),
    TestProcedure("8.4.1.2.5", "Analog Output, SBO, Execute Value does not match Select Value", Procedure84125),
    TestProcedure("8.4.1.2.6", "Analog Output, SBO, Select 0x28, Execute 0x17", Procedure84126),
    TestProcedure("8.4.1.2.7", "Analog Output, SBO, Configurable Device", Procedure84127),
    TestProcedure("8.4.1.2.8", "Analog Output, SBO, Same Sequence Number Select Retries", Procedure84128),
    TestProcedure("8.4.1.2.9", "Analog Output, SBO, Incrementing Sequence Number Select Retries", Procedure84129),
    TestProcedure("8.4.1.2.10", "Analog Output, SBO, Same Sequence Number Operate Retries", Procedure841210),
    TestProcedure("8.4.1.2.11", "Analog Output, SBO, Incrementing Sequence Number Operate Retries", Procedure841211),
    TestProcedure("8.4.1.2.12", "Analog Output, SBO, Sequence number checking", Procedure841212)
  )
}
