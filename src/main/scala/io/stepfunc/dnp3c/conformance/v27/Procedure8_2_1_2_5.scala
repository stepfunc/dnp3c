// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api.options.{ ControlOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.api.{ LabeledStep, OptionSource, ProcedureDefinition }
import io.stepfunc.dnp3c.conformance.common.{ OperationSteps, OperationType }
import io.stepfunc.dnp3c.dnp3.app.objects.CommandStatus

object Procedure8_2_1_2_5 extends ProcedureDefinition {

  override def requiredOptions = List(SupportOptions.Outputs.binary.commands)

  override def steps(options: OptionSource): Iterable[LabeledStep] = {

    val primary = ControlOptions.primary.getConfig(options)
    val alternate = ControlOptions.alternate.getConfig(options)

    val request1 = primary.oneByteHeader
    val request2 = primary.oneByteHeader(alternate.index)
    val reply = primary.oneByteHeader(alternate.index, x => x.copy(status = CommandStatus.NO_SELECT))

    new OperationSteps(OperationType.BinaryOutput).stepsSBOMismatch(request1, request2, reply)
  }

}
