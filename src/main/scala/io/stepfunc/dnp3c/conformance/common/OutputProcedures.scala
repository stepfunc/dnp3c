// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.common

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.OutstationOptions
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.Validations._
import io.stepfunc.dnp3c.dnp3.app.{ AppFunction, IIN, Sequence }
import io.stepfunc.dnp3c.dnp3.app.objects.CommandStatus

/**
 * The procedures that 8.2 (binary outputs) and 8.4 (analog outputs) both have, each written once for either output.
 * A procedure object extends one of these, and the section that lists it gives it a number and title.
 */
object OutputProcedures {

  sealed abstract class OutputProcedure(val output: OperationType) extends ProcedureDefinition {
    protected def operations: OperationSteps = new OperationSteps(output)
  }

  // an output procedure that needs the DUT to support the commands
  sealed abstract class CommandProcedure(output: OperationType) extends OutputProcedure(output) {
    override protected def requiredOptions: List[OptionCondition] = List(output.support.commands)
  }

  // an output procedure that needs the DUT to support the commands, and disabling its outputs: with them disabled,
  // the command (`function`) to output 0 is rejected, or for DIRECT OPERATE, NO ACK goes unanswered, and nothing
  // operates
  sealed abstract class ConfigurableDeviceProcedure(output: OperationType, function: AppFunction)
      extends OutputProcedure(output) {
    override protected def requiredOptions: List[OptionCondition] =
      List(output.support.commands, output.support.disabling)

    override protected def plan(options: OptionSource): Plan = {
      val outputs = Undoable(output.uninstall, PluginSteps.reinstallPoints)
      Plan(
        List(
          1 --> describe(s"The DUT's ${output.name}s can be uninstalled or disabled through its configuration"),
          2 --> outputs.apply,
          3 --> send(function, Sequence.zero, output.primary(options).twoByteHeader(Index.zero)),
          4 --> (
            if (function == AppFunction.directOpNoAck) expectNoAnswer(options)
            else expectSingleResponse(Sequence.zero, containingAnyOf(IIN.objectErrors))
          ),
          5 --> operations.expectNoOperation
        ),
        List(Label("restore") --> outputs.restore)
      )
    }
  }

  // SBO with 2-byte indexes; a DUT without the commands must reject them
  abstract class SelectBeforeOperateTwoByte(output: OperationType) extends OutputProcedure(output) {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val config = output.primary(options)
      if (options.get(output.support.commands))
        operations.SBO.supported(config.twoByteHeader, config.index.value, echoStep = 3)
      else operations.SBO.unsupported(config.twoByteHeader)
    }
  }

  // SBO with 1-byte indexes, on the alternate point
  abstract class SelectBeforeOperateOneByte(output: OperationType) extends CommandProcedure(output) {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val config = output.alternate(options)
      operations.SBO.supported(config.oneByteHeader, config.index.value, echoStep = 2)
    }
  }

  // a SELECT or DIRECT OPERATE to an uninstalled point, which the DUT must answer with NOT_SUPPORTED
  abstract class ToUninstalledPoint(output: OperationType, function: AppFunction) extends CommandProcedure(output) {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val config = output.primary(options)
      val index = output.uninstalledIndex(options)
      val request = config.twoByteHeader(index)
      val reply = config.twoByteHeader(index, c => output.withStatus(c, CommandStatus.NOT_SUPPORTED))
      operations.stepsUninstalledCommandPoint(function, request, reply)
    }
  }

  abstract class OperateAfterSelectTimeout(output: OperationType) extends CommandProcedure(output) {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val config = output.primary(options)
      val reply = config.oneByteHeader(c => output.withStatus(c, CommandStatus.TIMEOUT))
      operations.stepsSelectTimeout(options.get(OutstationOptions.selectTimeout), config.oneByteHeader, reply)
    }
  }

  abstract class SelectRetrySameSequence(output: OperationType) extends CommandProcedure(output) {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val config = output.primary(options)
      operations.stepsSelectRetrySameSequence(config.oneByteHeader, config.index.value)
    }
  }

  abstract class SelectRetryIncrementedSequence(output: OperationType) extends CommandProcedure(output) {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val config = output.primary(options)
      operations.stepsSelectRetryIncrementedSequence(config.oneByteHeader, config.index.value)
    }
  }

  abstract class OperateRetrySameSequence(output: OperationType) extends CommandProcedure(output) {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val config = output.primary(options)
      operations.stepsSameSequenceNumberOperateRetry(config.oneByteHeader, config.index.value)
    }
  }

  abstract class OperateRetryIncrementedSequence(output: OperationType) extends CommandProcedure(output) {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val config = output.primary(options)
      val error = config.oneByteHeader(c => output.withStatus(c, CommandStatus.NO_SELECT))
      operations.stepsIncrementingSequenceNumberOperateRetry(config.oneByteHeader, config.index.value, error)
    }
  }

  abstract class SequenceNumberChecking(output: OperationType) extends CommandProcedure(output) {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val config = output.primary(options)
      val response = config.oneByteHeader(c => output.withStatus(c, CommandStatus.NO_SELECT))
      operations.stepsSequenceNumbersOutOfOrder(config.oneByteHeader, config.index.value, response)
    }
  }

  // SELECT with 2-byte indexes, then OPERATE with 1-byte indexes, which the DUT must reject with NO_SELECT
  abstract class SelectTwoByteOperateOneByte(output: OperationType) extends CommandProcedure(output) {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val config = output.primary(options)
      val failure = config.oneByteHeader(c => output.withStatus(c, CommandStatus.NO_SELECT))
      operations.stepsSBOMismatch(config.twoByteHeader, config.oneByteHeader, failure)
    }
  }

  // a SELECT to a DUT whose outputs are disabled, which it must reject without operating
  abstract class SelectConfigurableDevice(output: OperationType)
      extends ConfigurableDeviceProcedure(output, AppFunction.select)

  // DIRECT OPERATE; a DUT without the commands must reject it
  abstract class DirectOperate(output: OperationType) extends OutputProcedure(output) {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val config = output.primary(options)
      if (options.get(output.support.commands)) operations.DO.supported(config.oneByteHeader, config.index.value)
      else operations.DO.unsupported(config.oneByteHeader)
    }
  }

  // a DIRECT OPERATE to a DUT whose outputs are disabled, which it must reject without operating
  abstract class DirectOperateConfigurableDevice(output: OperationType)
      extends ConfigurableDeviceProcedure(output, AppFunction.directOp)

  // DIRECT OPERATE, NO ACKNOWLEDGE: no response, and the output operates if the DUT has the commands
  abstract class DirectOperateNoAck(output: OperationType) extends OutputProcedure(output) {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val config = output.primary(options)
      val steps1to3 = List(
        1 --> send(AppFunction.directOpNoAck, Sequence.zero, config.oneByteHeader),
        2 --> expectNoAnswer(options),
        3 --> describe(s"For a DUT without ${output.name}s, this section ends here")
      )
      if (options.get(output.support.commands)) steps1to3 :+ (4 --> operations.expectOperation(config.index.value))
      else steps1to3
    }
  }

  abstract class DirectOperateNoAckToUninstalledPoint(output: OperationType) extends CommandProcedure(output) {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val header = output.primary(options).twoByteHeader(output.uninstalledIndex(options))
      List(
        1 --> send(AppFunction.directOpNoAck, Sequence.zero, header),
        2 --> expectNoAnswer(options),
        3 --> operations.expectNoOperation
      )
    }
  }

  abstract class DirectOperateNoAckConfigurableDevice(output: OperationType)
      extends ConfigurableDeviceProcedure(output, AppFunction.directOpNoAck)

  // several command objects in one request
  abstract class MultipleObjects(output: OperationType) extends CommandProcedure(output) {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] =
      new MultiOperationSteps(output).steps(options)
  }

}
