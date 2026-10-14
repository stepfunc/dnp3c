// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.helpers

import io.stepfunc.dnp3c.conformance.api.{ LabeledStep, OptionSource, ProcedureDefinition }
import io.stepfunc.dnp3c.dnp3.app.objects.{ CommandStatus, Group12Var1 }

trait SBOWithManipulatedControl extends ProcedureDefinition {

  def modify(control: Group12Var1): Group12Var1

  private val output = OperationType.BinaryOutput

  override def requiredOptions = List(output.support.commands)

  final override def steps(options: OptionSource): Iterable[LabeledStep] = {

    val primary = output.primary(options)

    val request1 = primary.oneByteHeader
    val request2 = primary.oneByteHeader(x => modify(x))
    val reply = primary.oneByteHeader(x => modify(x).copy(status = CommandStatus.NO_SELECT))

    new OperationSteps(output).stepsSBOMismatch(request1, request2, reply)

  }
}
