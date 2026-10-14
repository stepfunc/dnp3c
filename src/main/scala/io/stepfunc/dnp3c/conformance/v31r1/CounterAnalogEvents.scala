// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ CounterOptions, OutstationOptions }
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.conformance.v27
import io.stepfunc.dnp3c.conformance.common.CounterEventSteps
import io.stepfunc.dnp3c.conformance.v27.AnalogInputChange
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group22Var0, Group32Var0 }

/**
 * 3.1r1's 8.17 (counter events) and 8.19 (analog input events). A subset level 1 device may decline reads of the
 * events, which a test of its own checks, and every other test runs only where the events can be read. The 0x06 test of
 * counter events leaves its first response unconfirmed, so that the next read reports those events again with the new
 * ones; the other changes only number the confirmations the master sends as steps of their own.
 *
 * 3.1r1 also lets a counter event be a change of flags alone (TB2017-003a): the plugin's actions that generate counter
 * events, and its checks of them, decide which kind they are.
 */
object CounterAnalogEvents {

  private val counterEventReads = OptionCondition(Inputs.counter.eventReads)
  private val analogEventReads = OptionCondition(Inputs.analog.eventReads)

  private def counterTest(build: OptionSource => List[LabeledStep]): ProcedureDefinition = new ProcedureDefinition {
    override protected def requiredOptions: List[OptionCondition] = Inputs.counter.withEvents :+ counterEventReads
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = build(options)
  }

  val counterLevelOne = new StaticInputs.LevelOneWithoutReads(Inputs.counter, List(Group22Var0), events = true)
  val counterWithout = v27.Procedure8_17_2_1.requiring(counterEventReads)
  val counterAll: ProcedureDefinition = counterTest(_ => CounterEventSteps.AllQualifierSteps(unconfirmed = true))
  val counterOneByteCount: ProcedureDefinition = counterTest(options =>
    CounterEventSteps.CountQualifierSteps(
      options.get(CounterOptions.numEventsGenerated),
      twoBytesQualifier = false,
      confirmSteps = true))
  val counterTwoByteCount: ProcedureDefinition = counterTest(options =>
    CounterEventSteps.CountQualifierSteps(
      options.get(CounterOptions.numEventsGenerated),
      twoBytesQualifier = true,
      confirmSteps = true))
  val counterWithoutConfirm = v27.Procedure8_17_2_5.requiring(counterEventReads)

  val analogLevelOne = new StaticInputs.LevelOneWithoutReads(Inputs.analog, List(Group32Var0), events = true)
  val analogChange = new AnalogInputChange(confirmStep = true).requiring(analogEventReads)
  val analogWithoutConfirm = v27.Procedure8_19_2_2.requiring(analogEventReads)
}
