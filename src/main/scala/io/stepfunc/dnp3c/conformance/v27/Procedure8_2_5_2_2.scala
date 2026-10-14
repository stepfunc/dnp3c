// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.ControlOptions.ControlCodePointOptions
import io.stepfunc.dnp3c.conformance.api.options.{ ControlOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.common.{ OperationSteps, OperationType }
import io.stepfunc.dnp3c.dnp3.app.objects.ControlCode
import io.stepfunc.dnp3c.dnp3.app.MutableSequence

object Procedure8_2_5_2_2 extends ProcedureDefinition {

  override protected def requiredOptions = List(SupportOptions.Outputs.binary.commands)

  override def steps(options: OptionSource): Iterable[LabeledStep] = {
    val operations = new OperationSteps(OperationType.BinaryOutput)
    // each request gets its own sequence number, so a repeated control isn't taken for a retry
    val seq = MutableSequence.zero

    def singleFunctionSteps(
        stepNum: Int,
        config: ControlCodePointOptions,
        funcCode: ControlCode,
        funcName: String): Iterable[LabeledStep] = {
      val formattedFuncCode = "0x%02x".format(funcCode.value)

      if (config.isPresent(options)) {
        val index = options.get(config.index).get.value
        val header = config.getConfig(options, funcCode).get.oneByteHeader

        List(
          stepNum -->
            describe(s"DUT supports the $formattedFuncCode ($funcName) control code for single function binary output point at index $index."),
          stepNum + 1 --> operations.directOperate(header, seq.increment()),
          stepNum + 2 --> operations.expectOperation(index)
        )
      } else {
        List((stepNum to stepNum + 2) -->
          describe(f"The DUT doesn't support control code $formattedFuncCode ($funcName) on a single-function binary output at this index; skipping."))
      }
    }

    List(3 --> describe("The plugin knows what each control below should do, and checks that it happened")) ++
      singleFunctionSteps(4, ControlOptions.singleFunctionPulseOnPoint, ControlCode.pulse, "Pulse On/NUL") ++
      singleFunctionSteps(7, ControlOptions.singleFunctionLatchOnPoint, ControlCode.latchOn, "Latch On/NUL") ++
      singleFunctionSteps(
        10,
        ControlOptions.singleFunctionLatchOffPoint,
        ControlCode.latchOff,
        "Latch Off/NUL") ++
      singleFunctionSteps(
        13,
        ControlOptions.singleFunctionPulseOnClosePoint,
        ControlCode.pulseClose,
        "Pulse On/Close") ++
      singleFunctionSteps(
        16,
        ControlOptions.singleFunctionPulseOnTripPoint,
        ControlCode.pulseTrip,
        "Pulse On/Trip")
  }

}
