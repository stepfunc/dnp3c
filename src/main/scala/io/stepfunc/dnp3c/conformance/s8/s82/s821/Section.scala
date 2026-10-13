// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s82.s821

import io.stepfunc.dnp3c.conformance.api.{ TestProcedure, TestSection }

object Section extends TestSection {

  def id = "8.2.1"

  def description = "Select Before Operate"

  override def procedures: Iterable[TestProcedure] = List(
    TestProcedure("8.2.1.2.1", "Binary Output, SBO, 0x28", Procedure82121),
    TestProcedure("8.2.1.2.2", "Binary Output, SBO, 0x17", Procedure82122),
    TestProcedure("8.2.1.2.3", "Binary Output, SBO, To Uninstalled Point", Procedure82123),
    TestProcedure("8.2.1.2.4", "Binary Output, SBO, Execute Issued After Timeout", Procedure82124),
    TestProcedure("8.2.1.2.5", "Binary Output, SBO, Execute to Different Point Than Select", Procedure82125),
    TestProcedure("8.2.1.2.6", "Binary Output, SBO, Execute On Time does not match Select On Time", Procedure82126),
    TestProcedure("8.2.1.2.7", "Binary Output, SBO, Execute Off Time does not match Select Off Time", Procedure82127),
    TestProcedure(
      "8.2.1.2.8",
      "Binary Output, SBO, Select with supported Code, Execute with different Code",
      Procedure82128),
    TestProcedure("8.2.1.2.9", "Binary Output, SBO, Select 0x28, Execute 0x17", Procedure82129),
    TestProcedure("8.2.1.2.10", "Binary Output, SBO, Configurable Device", Procedure821210),
    TestProcedure("8.2.1.2.11", "Binary Output, SBO, Same Sequence Number Select Retries", Procedure821211),
    TestProcedure("8.2.1.2.12", "Binary Output, SBO, Incrementing Sequence Number Select Retries", Procedure821212),
    TestProcedure("8.2.1.2.13", "Binary Output, SBO, Same Sequence Number Operate Retries", Procedure821213),
    TestProcedure("8.2.1.2.14", "Binary Output, SBO, Incrementing Sequence Number Operate Retries", Procedure821214),
    TestProcedure("8.2.1.2.15", "Binary Output, SBO, Sequence number checking", Procedure821215)
  )

}
