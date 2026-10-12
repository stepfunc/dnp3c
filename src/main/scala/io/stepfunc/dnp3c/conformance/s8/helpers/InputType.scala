// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.helpers

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{
  BinaryInputOptions, DoubleBitInputOptions, SupportOptions, TimeSyncOptions
}
import io.stepfunc.dnp3c.dnp3.MessageValidation
import io.stepfunc.dnp3c.dnp3.app.{ IIN, Variation }
import io.stepfunc.dnp3c.dnp3.app.objects._

/**
 * The binary (g1, g2) or double-bit (g3, g4) inputs: the 8.14 and 8.22 procedures test their events the same way,
 * with these options, objects, actions and checks
 */
sealed abstract class InputType(val name: String, val support: SupportOptions.InputOptions) {
  def numEventsGenerated: IntegerOption
  def withTime: BooleanOption
  def withRelativeTime: BooleanOption

  def anyVariation: Variation
  def noTimeVariation: Variation
  def absoluteTimeVariation: Variation
  def relativeTimeVariation: Variation

  // the response to a request for an unsupported time variation
  def unsupportedVariationResponse: MessageValidation

  def generateEvents: TestStep
  def generateExactEvents(numEvents: Int): TestStep
  def verifyLatestEvents: MessageValidation
  def verifyFirstEvent: MessageValidation
  def verifyRestEvents: MessageValidation
  def verifyOldAndNewEvents: MessageValidation
}

object InputType {
  object BinaryInput extends InputType("binary input", SupportOptions.Inputs.binary) {
    def numEventsGenerated: IntegerOption = BinaryInputOptions.numBinaryEventsGenerated
    def withTime: BooleanOption = TimeSyncOptions.binaryInputChangeWithTime
    def withRelativeTime: BooleanOption = TimeSyncOptions.binaryInputChangeWithRelativeTime

    def anyVariation: Variation = Group2Var0
    def noTimeVariation: Variation = Group2Var1
    def absoluteTimeVariation: Variation = Group2Var2
    def relativeTimeVariation: Variation = Group2Var3

    // 8.14 requires an empty response with IIN2-1
    def unsupportedVariationResponse: MessageValidation =
      Validations.isEmpty && Validations.containing(IIN.OBJECT_UNKNOWN)

    def generateEvents: TestStep = PluginSteps.generateBinaryInputChangeEvents()
    def generateExactEvents(numEvents: Int): TestStep = PluginSteps.generateExactBinaryInputChangeEvents(numEvents)
    def verifyLatestEvents: MessageValidation = PluginValidations.verifyLatestBinaryInputChangeEvents()
    def verifyFirstEvent: MessageValidation = PluginValidations.verifyFirstBinaryInputChangeEvent()
    def verifyRestEvents: MessageValidation = PluginValidations.verifyRestBinaryInputChangeEvents()
    def verifyOldAndNewEvents: MessageValidation = PluginValidations.verifyOldAndNewBinaryInputChangeEvents()
  }

  object DoubleBitBinaryInput extends InputType("double-bit input", SupportOptions.Inputs.doubleBit) {
    def numEventsGenerated: IntegerOption = DoubleBitInputOptions.numDoubleBitEventsGenerated
    def withTime: BooleanOption = TimeSyncOptions.doubleBitInputChangeWithTime
    def withRelativeTime: BooleanOption = TimeSyncOptions.doubleBitInputChangeWithRelativeTime

    def anyVariation: Variation = Group4Var0
    def noTimeVariation: Variation = Group4Var1
    def absoluteTimeVariation: Variation = Group4Var2
    def relativeTimeVariation: Variation = Group4Var3

    // 8.22 requires a Null Response; IIN2-1 is also accepted, since it is what 8.14 asks for
    def unsupportedVariationResponse: MessageValidation = Validations.isNullOrErrorResponse(IIN.OBJECT_UNKNOWN)

    def generateEvents: TestStep = PluginSteps.generateDoubleBitBinaryInputChangeEvents()
    def generateExactEvents(numEvents: Int): TestStep =
      PluginSteps.generateExactDoubleBitBinaryInputChangeEvents(numEvents)
    def verifyLatestEvents: MessageValidation = PluginValidations.verifyLatestDoubleBitBinaryInputChangeEvents()
    def verifyFirstEvent: MessageValidation = PluginValidations.verifyFirstDoubleBitBinaryInputChangeEvent()
    def verifyRestEvents: MessageValidation = PluginValidations.verifyRestDoubleBitBinaryInputChangeEvents()
    def verifyOldAndNewEvents: MessageValidation =
      PluginValidations.verifyOldAndNewDoubleBitBinaryInputChangeEvents()
  }
}
