// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s82.s821

import io.stepfunc.dnp3c.conformance.api.options.{ ControlOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.api.{ LabeledStep, OptionSource, TestProcedure }
import io.stepfunc.dnp3c.conformance.s8.helpers.{ OperationSteps, OperationType }
import io.stepfunc.dnp3c.dnp3.app.objects.CommandStatus

object Procedure82125 extends TestProcedure {

  def id = "8.2.1.2.5"

  def description = "Binary Output, SBO, Execute to Different Point Than Select"

  override def requiredOptions = List(SupportOptions.Outputs.binary.commands)

  def steps(options: OptionSource): Iterable[LabeledStep] = {

    val primary = ControlOptions.primary.getConfig(options)
    val alternate = ControlOptions.alternate.getConfig(options)

    val request1 = primary.oneByteHeader
    val request2 = primary.oneByteHeader(alternate.index)
    val reply = primary.oneByteHeader(alternate.index, x => x.copy(status = CommandStatus.NO_SELECT))

    new OperationSteps(OperationType.BinaryOutput).stepsSBOMismatch(request1, request2, reply)
  }

}
