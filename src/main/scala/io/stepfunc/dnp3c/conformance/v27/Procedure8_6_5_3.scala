// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.OperationType
import io.stepfunc.dnp3c.conformance.common.Validations._
import io.stepfunc.dnp3c.dnp3.app.{ AppFunction, IIN, ObjectHeader, Sequence }
import io.stepfunc.dnp3c.dnp3.{ AutoConfirm, LinkOverride, Requests }
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE

object Procedure8_6_5_3 extends ProcedureDefinition {

  private val broadcast = UInt16LE(0xFFFF)

  // the procedure needs a binary or an analog output
  override protected def requirement: Requirement =
    Requirement.anyOf(
      OptionCondition(SupportOptions.Outputs.binary.commands),
      OptionCondition(SupportOptions.Outputs.analog.commands))

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {

    // use a binary output (g12v1), or an analog output (g41v2) for a DUT without binary outputs
    val output =
      if (options.get(SupportOptions.Outputs.binary.commands)) OperationType.BinaryOutput
      else OperationType.AnalogOutput
    val primary = output.primary(options)
    steps(
      primary.oneByteHeader,
      s"${output.name} ${primary.index.value}",
      PluginSteps.checkOperate(output.kind, primary.index.value),
      options)
  }

  private def steps(
      header: ObjectHeader,
      output: String,
      checkOperate: PluginCheck,
      options: OptionSource): List[LabeledStep] = {
    List(
      // steps 3-4 expect no frame right after the broadcast, so a manual tester watches the output throughout and is
      // asked after the steps
      1 --> PluginSteps.prepare(
        s"Watch $output: it should operate once after the broadcast in step 1. You'll be asked what you saw after " +
          "the steps. Get ready, then press ENTER.") +
        send(AppFunction.directOpNoAck, Sequence.zero, header, LinkOverride.destination(broadcast)),
      2 --> checkOperate.deferrable,
      (3 to 4) --> expectNoAnswer(options),
      5 --> send(Requests.readClass1(Sequence.two)),
      (6 to 7) --> expectAtLeastOneResponse(
        Sequence.two,
        verifyConfirmRequestedIfRequired.toResponseValidation && containing(IIN.BROADCAST).onFirstFragment),
      8 --> send(Requests.readClass1(Sequence.three)),
      9 --> expectSingleResponse(Sequence.three, isNull && without(IIN.BROADCAST))
    )
  }

}
