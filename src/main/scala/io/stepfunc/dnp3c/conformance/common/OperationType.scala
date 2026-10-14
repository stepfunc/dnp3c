// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.common

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{
  AnalogOutputOptions, CommandConfig, BinaryOutputOptions, SupportOptions
}
import io.stepfunc.dnp3c.dnp3.app.FixedSizeSerializable
import io.stepfunc.dnp3c.dnp3.app.objects.{ CommandStatus, Group12Var1, Group41Var2 }

/**
 * The binary (CROB, g12v1) or analog (g41v2) outputs: the 8.2 and 8.4 procedures test them the same way, with these
 * options, commands and checks
 */
sealed abstract class OperationType(val name: String, val support: SupportOptions.OutputOptions) {
  type Command <: FixedSizeSerializable

  def primary(options: OptionSource): CommandConfig[Command]
  def alternate(options: OptionSource): CommandConfig[Command]
  def uninstalledIndex(options: OptionSource): Index
  def installedPoints(options: OptionSource): List[Index]
  def maxPerRequest(options: OptionSource): Int
  def withStatus(command: Command, status: CommandStatus): Command

  def uninstall: TestStep
  def checkOperate(index: Int): PluginCheck
  def checkOperate(indices: List[Int]): PluginCheck
  def checkNotOperate: PluginCheck
}

object OperationType {
  object BinaryOutput extends OperationType("binary output", SupportOptions.Outputs.binary) {
    type Command = Group12Var1

    def primary(options: OptionSource): CommandConfig[Group12Var1] = BinaryOutputOptions.primary.getConfig(options)
    def alternate(options: OptionSource): CommandConfig[Group12Var1] = BinaryOutputOptions.alternate.getConfig(options)
    def uninstalledIndex(options: OptionSource): Index = options.get(BinaryOutputOptions.uninstalledIndex)
    def installedPoints(options: OptionSource): List[Index] = options.get(BinaryOutputOptions.installedPoints)
    def maxPerRequest(options: OptionSource): Int = options.get(BinaryOutputOptions.maxPointsInSingleRequest)
    def withStatus(command: Group12Var1, status: CommandStatus): Group12Var1 = command.copy(status = status)

    def uninstall: TestStep = PluginSteps.uninstallBinaryOutputs
    def checkOperate(index: Int): PluginCheck = PluginSteps.checkBinaryOutputOperate(index)
    def checkOperate(indices: List[Int]): PluginCheck = PluginSteps.checkBinaryOutputsOperate(indices)
    def checkNotOperate: PluginCheck = PluginSteps.checkBinaryOutputNotOperate()
  }

  object AnalogOutput extends OperationType("analog output", SupportOptions.Outputs.analog) {
    type Command = Group41Var2

    def primary(options: OptionSource): CommandConfig[Group41Var2] = AnalogOutputOptions.primary.getConfig(options)
    def alternate(options: OptionSource): CommandConfig[Group41Var2] = AnalogOutputOptions.alternate.getConfig(options)
    def uninstalledIndex(options: OptionSource): Index = options.get(AnalogOutputOptions.uninstalledIndex)
    def installedPoints(options: OptionSource): List[Index] = options.get(AnalogOutputOptions.installedPoints)
    def maxPerRequest(options: OptionSource): Int = options.get(AnalogOutputOptions.maxPointsInSingleRequest)
    def withStatus(command: Group41Var2, status: CommandStatus): Group41Var2 = command.copy(status = status)

    def uninstall: TestStep = PluginSteps.uninstallAnalogOutputs
    def checkOperate(index: Int): PluginCheck = PluginSteps.checkAnalogOutputOperate(index)
    def checkOperate(indices: List[Int]): PluginCheck = PluginSteps.checkAnalogOutputsOperate(indices)
    def checkNotOperate: PluginCheck = PluginSteps.checkAnalogOutputNotOperate()
  }
}
