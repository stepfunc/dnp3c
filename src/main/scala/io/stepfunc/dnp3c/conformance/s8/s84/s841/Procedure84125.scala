// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s84.s841

import io.stepfunc.dnp3c.conformance.api.options.{ AnalogOutputOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.api.{ LabeledStep, OptionSource, TestProcedure }
import io.stepfunc.dnp3c.conformance.s8.helpers.{ OperationSteps, OperationType }
import io.stepfunc.dnp3c.dnp3.app.objects.CommandStatus

object Procedure84125 extends TestProcedure {

  override def id: String = "8.4.1.2.5"

  override def description: String = "Analog Output, SBO, Execute Value does not match Select Value"

  override def requiredOptions = List(SupportOptions.Outputs.analog.commands)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {

    val primary = AnalogOutputOptions.primary.getConfig(options)
    val alternateValue = primary.command.value + 1

    val request1 = primary.oneByteHeader
    val request2 = primary.oneByteHeader(x => x.copy(value = alternateValue.toShort))
    val reply = primary.oneByteHeader(x => x.copy(value = alternateValue.toShort, status = CommandStatus.NO_SELECT))

    new OperationSteps(OperationType.AnalogOutput).stepsSBOMismatch(request1, request2, reply)
  }
}
