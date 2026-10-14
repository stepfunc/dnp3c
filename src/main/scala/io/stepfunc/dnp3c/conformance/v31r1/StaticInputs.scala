// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.{ InputOptions, Inputs }
import io.stepfunc.dnp3c.conformance.s8.helpers.ConfigurableInputProcedure
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.conformance.{ s8 }
import io.stepfunc.dnp3c.dnp3.app.{ IIN, Sequence, Variation }
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group1Var0, Group20Var0, Group20Var1, Group21Var0, Group30Var0, Group3Var0 }
import io.stepfunc.dnp3c.dnp3.{ Comment, DeferredCheck, MessageValidation, Requests, TestDriver }

/**
 * 3.1r1's tests of reads of static inputs: 8.13 (binary inputs), 8.21 (the same tests with double-bit inputs), 8.16
 * (counters and frozen counters) and 8.18 (analog inputs). What changed from 2.7: a device without the inputs, or with
 * them all uninstalled, answers with IIN2.1 or IIN2.2 (TB2016-001), where 2.7 took a Null Response; and a subset level
 * 1 device may decline reads of the inputs altogether.
 */
object StaticInputs {

  private val objectUnknownOrParameterError = IIN.OBJECT_UNKNOWN | IIN.PARAM_ERROR

  /**
   * 8.13.2-1, 8.16.1.2.1, 8.16.2.2.1 and 8.18.2-1: a subset level 1 device that can't read the inputs answers each read
   * of them with a Null Error Response, IIN2.0, 2.1 or 2.2, which is noted. (8.16.1.2.1 accepts "any error
   * indication", which these are.)
   *
   * @param variations what it reads, one request each: 8.16.1.2.1 reads g20v0 and g20v1
   * @param condition whether step 1 states the condition the test runs on, as 8.16.1.2.1 and 8.16.2.2.1 do
   * @param events whether it's the input's events the device can't read, as in 8.17.2-1 and 8.19.2-1, not its points
   */
  final class LevelOneWithoutReads(
      input: InputOptions,
      variations: List[Variation],
      condition: Boolean = false,
      events: Boolean = false)
      extends ProcedureDefinition {
    override protected def requirement: Requirement = !(if (events) input.eventReads else input.reads)

    private val what = if (events) s"${input.longName} events" else s"${input.longName} points"
    private val tests = if (events) s"${input.longName} event" else input.longName

    private val errors = IIN.FUNC_NOT_SUPPORTED | IIN.OBJECT_UNKNOWN | IIN.PARAM_ERROR

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val first = if (condition) 2 else 1
      val reads = variations.zipWithIndex.flatMap { (variation, i) =>
        val seq = Sequence((1 + i).toByte)
        List(
          first + 2 * i --> send(Requests.readAllObjects(seq, variation)),
          first + 2 * i + 1 --> declined(seq, variation))
      }
      Option.when(condition)(
        1 --> describe(s"Runs only for a subset level 1 device that can't read $what")).toList ++
        reads :+
        (first + 2 * variations.size --> describe(s"No further $tests tests apply to this DUT"))
    }

    private def declined(seq: Sequence, variation: Variation): TestStep = new TestStep {
      private val check = isEmpty && containingAnyOf(errors)
      def description: List[Comment] = check.description :+
        Comment("Note the error IIN bits set (IIN2.1 is preferred)")
      def run(driver: TestDriver): List[DeferredCheck] = {
        var set = IIN.empty
        val checks =
          expectSingleResponse(
            seq,
            check && MessageValidation.check("") { m =>
              set = m.iin.getOrElse(IIN.empty) & errors
            }).run(driver)
        driver.reporter.note(s"the DUT answers a read of $variation with $set")
        checks
      }
    }
  }

  /** 8.13.2-2: a device without the inputs answers a read of them with an error response, IIN2.1 or IIN2.2 */
  final class WithoutInputs(input: InputOptions, variation: Variation) extends ProcedureDefinition {
    override protected def requirement: Requirement =
      Requirement.all(!input.points, OptionCondition(input.reads))
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = List(
      1 --> send(Requests.readAllObjects(Sequence.one, variation)),
      2 --> expectSingleResponse(Sequence.one, containingAnyOf(objectUnknownOrParameterError))
    )
  }

  /**
   * 8.16.1.2.2, 8.16.2.2.2 and 8.18.2-2: a device without the points answers a read of them with a Null Error Response,
   * IIN2.1 or IIN2.2 (8.13.2-2 allows an Error Response with data)
   */
  final class WithoutPoints(input: InputOptions, variation: Variation) extends ProcedureDefinition {
    override protected def requirement: Requirement =
      Requirement.all(!input.points, OptionCondition(input.reads))
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = List(
      1 --> send(Requests.readAllObjects(Sequence.one, variation)),
      2 --> expectSingleResponse(Sequence.one, isNullErrorResponse(objectUnknownOrParameterError))
    )
  }

  /** 8.13.2-4 and 8.16.1.2.4: with every point uninstalled or disabled, a read gets a Null Error Response with IIN2.1 or IIN2.2 */
  final class Configurable(
      input: InputOptions,
      points: String,
      uninstall: TestStep,
      variation: Variation,
      seq: Sequence)
      extends ConfigurableInputProcedure(
        points,
        uninstall,
        variation,
        seq,
        isNullErrorResponse(objectUnknownOrParameterError)) {
    override protected def requiredOptions: List[OptionCondition] = input.withDisabling :+ OptionCondition(input.reads)
  }

  val binaryLevelOne = new LevelOneWithoutReads(Inputs.binary, List(Group1Var0))
  val binaryWithout = new WithoutInputs(Inputs.binary, Group1Var0)
  val binaryHas = s8.s813.Procedure81322.requiring(OptionCondition(Inputs.binary.reads))
  val binaryConfigurable =
    new Configurable(Inputs.binary, "binary inputs", PluginSteps.uninstallBinaryInputs, Group1Var0, Sequence.one)

  val doubleBitLevelOne = new LevelOneWithoutReads(Inputs.doubleBit, List(Group3Var0))
  val doubleBitWithout = new WithoutInputs(Inputs.doubleBit, Group3Var0)
  val doubleBitHas = s8.s821.Procedure82121.requiring(OptionCondition(Inputs.doubleBit.reads))
  val doubleBitConfigurable = new Configurable(
    Inputs.doubleBit,
    "double-bit inputs",
    PluginSteps.uninstallDoubleBitBinaryInputs,
    Group3Var0,
    Sequence.zero)

  val counterLevelOne = new LevelOneWithoutReads(Inputs.counter, List(Group20Var0, Group20Var1), condition = true)
  val counterWithout = new WithoutPoints(Inputs.counter, Group20Var0)
  val counterHas = s8.s816.Procedure816122.requiring(OptionCondition(Inputs.counter.reads))
  val counterConfigurable =
    new Configurable(Inputs.counter, "counters", PluginSteps.uninstallCounterInputs, Group20Var0, Sequence.one)

  val frozenCounterLevelOne = new LevelOneWithoutReads(Inputs.frozenCounter, List(Group21Var0), condition = true)
  val frozenCounterWithout = new WithoutPoints(Inputs.frozenCounter, Group21Var0)

  val analogLevelOne = new LevelOneWithoutReads(Inputs.analog, List(Group30Var0))
  val analogWithout = new WithoutPoints(Inputs.analog, Group30Var0)
  val analogHas = s8.s818.Procedure81822.requiring(OptionCondition(Inputs.analog.reads))
}
