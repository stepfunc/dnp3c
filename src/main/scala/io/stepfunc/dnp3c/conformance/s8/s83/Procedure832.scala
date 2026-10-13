// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s83

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ AnalogOutputOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects._
import io.stepfunc.dnp3c.dnp3.{ MessageValidation, ResponseValidation }

object Procedure832 extends ProcedureDefinition {

  def steps(options: OptionSource): List[LabeledStep] = {

    val header = AnalogOutputOptions.primary.getConfig(options).twoByteHeader
    val idx = options.get(AnalogOutputOptions.primary.index)
    val value = options.get(AnalogOutputOptions.primary.value)
    val steps1to2 = List(
      1 --> {
        send(AppFunction.directOp, Sequence.zero, header) +
          (if (options.get(SupportOptions.Outputs.analog.commands)) expectSingleResponse(Sequence.zero, echoes(header))
           else
             describe("The DUT has no analog output commands, so any response to the operate is accepted") +
               expectSingleResponse(Sequence.zero))
      },
      2 --> send(AppFunction.read, Sequence.one, AllObjectsHeader(Group40Var0))
    )

    val steps3to6 = if (options.get(SupportOptions.Outputs.analog.statusPoints)) {
      List(
        (3 to 6) --> expectAtLeastOneResponse(
          Sequence.one,
          // the value is only checked if step 1 could write it, in whichever fragment reports the output
          (containsAnalogOutputStatusOnly && verifyConfirmRequestedIfRequired).toResponseValidation &&
            (if (options.get(SupportOptions.Outputs.analog.commands))
               PluginValidations.checkAnalogOutputTolerance(idx.value, value).acrossFragments
             else ResponseValidation.none)
        )
      )
    } else {
      List(
        (3 to 4) --> describe("Device does not support Analog Output."),
        5 --> expectSingleResponse(
          Sequence.one,
          isNullOrErrorResponse(IIN.OBJECT_UNKNOWN | IIN.PARAM_ERROR)),
        6 --> describe("Device does not support Analog Output.")
      )
    }

    steps1to2 ::: steps3to6
  }

  // step 3: analog output status (g40v2), with 1- or 2-octet start/stop indexes
  val containsAnalogOutputStatusOnly: MessageValidation = isNotEmpty && verifyStaticResponse(Set(Group40Var2))

}
