// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ AnalogOutputOptions, CommandConfig }
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Outputs
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.{ CommandStatus, Group41Var1 }

/**
 * 3.1r1's 8.4.1.2.13, Analog Output, SBO, 32-bit values: a select with g41v1 and a value of 70,000, which only a 32-bit
 * point can take. A DUT without 32-bit commands, which only subset levels 1 and 2 may be, answers with IIN2.1; one
 * with them but no 32-bit point installed answers with status code 12 (out of range); one with a 32-bit point echoes
 * the select, and operates the point on the execute that follows.
 */
object AnalogOutput32 {

  private val value = 70000

  private[v31r1] def select(index: Int, status: CommandStatus = CommandStatus.SUCCESS): ObjectHeader =
    CommandConfig(Index(index), Group41Var1(value, status)).twoByteHeader

  val sbo: ProcedureDefinition = new ProcedureDefinition {
    override protected def requiredOptions: List[OptionCondition] = List(Outputs.analog.commands)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val point32 = options.get(AnalogOutputOptions.index32Bit)
      val first = options.get(AnalogOutputOptions.installedPoints).headOption
        .getOrElse(options.get(AnalogOutputOptions.primary.index))
      val index = point32.getOrElse(first).value
      val header = select(index)
      val start = List(
        1 --> describe(
          point32.fold(s"The DUT has no 32-bit analog output installed: use the first point, $index")(i =>
            s"Use the 32-bit analog output ${i.value}")),
        2 --> send(AppFunction.select, Sequence.zero, header)
      )
      if (!options.get(AnalogOutputOptions.commands32Bit))
        start ++ List(
          3 --> expectSingleResponse(Sequence.zero, containing(IIN.OBJECT_UNKNOWN)),
          (4 to 8) --> describe("The DUT doesn't support 32-bit analog outputs: no further steps apply")
        )
      else if (point32.isEmpty)
        start ++ List(
          3 --> describe("The DUT supports 32-bit analog outputs"),
          4 --> expectSingleResponse(Sequence.zero, matches(select(index, CommandStatus.OUT_OF_RANGE))),
          (5 to 8) --> describe("The DUT has no 32-bit analog output installed: no further steps apply")
        )
      else
        start ++ List(
          (3 to 4) --> describe("The DUT has a 32-bit analog output installed"),
          5 --> expectSingleResponse(Sequence.zero, echoes(header)),
          6 --> send(AppFunction.operate, Sequence.one, header),
          7 --> expectSingleResponse(Sequence.one, echoes(header)),
          8 --> PluginSteps.checkAnalogOutputOperate(index)
        )
    }
  }
}
