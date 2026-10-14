// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api.options.{ AnalogOutputOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.api.{ LabeledStep, OptionSource, ProcedureDefinition }
import io.stepfunc.dnp3c.conformance.common.{ OperationSteps, OperationType }
import io.stepfunc.dnp3c.dnp3.app.objects.CommandStatus

object Procedure8_4_1_2_5 extends ProcedureDefinition {

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
