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
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group1Var0, Group3Var0 }
import io.stepfunc.dnp3c.dnp3.{ Comment, DeferredCheck, MessageValidation, Requests, TestDriver }

/**
 * 3.1r1's 8.13 (binary inputs) and 8.21 (the same tests with double-bit inputs). What changed from 2.7: a device
 * without the inputs, or with them all uninstalled, answers with IIN2.1 or IIN2.2 (TB2016-001), where 2.7 took a Null
 * Response; and a subset level 1 device may decline reads of the inputs altogether.
 */
object StaticInputs {

  private val objectUnknownOrParameterError = IIN.OBJECT_UNKNOWN | IIN.PARAM_ERROR

  /** 8.13.2-1: a subset level 1 device that can't read the inputs answers with IIN2.0, 2.1 or 2.2, which is noted */
  final class LevelOneWithoutReads(input: InputOptions, variation: Variation) extends ProcedureDefinition {
    override protected def requirement: Requirement = !input.reads

    private val errors = IIN.FUNC_NOT_SUPPORTED | IIN.OBJECT_UNKNOWN | IIN.PARAM_ERROR

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = List(
      1 --> send(Requests.readAllObjects(Sequence.one, variation)),
      2 --> new TestStep {
        private val check = isEmpty && containingAnyOf(errors)
        def description: List[Comment] = check.description :+
          Comment("Note the error IIN bits set (IIN2.1 is preferred)")
        def run(driver: TestDriver): List[DeferredCheck] = {
          var set = IIN.empty
          val checks =
            expectSingleResponse(
              Sequence.one,
              check && MessageValidation.check("") { m =>
                set = m.iin.getOrElse(IIN.empty) & errors
              }).run(driver)
          driver.reporter.note(s"the DUT answers a read of $variation with $set")
          checks
        }
      },
      3 --> describe(s"No further ${input.longName} tests apply to this DUT")
    )
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

  /** 8.13.2-4: with every point uninstalled or disabled, a read gets a Null Error Response with IIN2.1 or IIN2.2 */
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

  /** a 2.7 definition that 3.1r1 runs only where `extra` also holds, e.g. only for a device whose points can be read */
  final class Requiring(inner: ProcedureDefinition, extra: OptionCondition*) extends ProcedureDefinition {
    override def incomplete: Boolean = inner.incomplete
    override def disabledBy(options: OptionSource): Option[Unmet] =
      Requirement.all(extra*).unmet(options).orElse(inner.disabledBy(options))
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = inner.configure(options).steps
  }

  val binaryLevelOne = new LevelOneWithoutReads(Inputs.binary, Group1Var0)
  val binaryWithout = new WithoutInputs(Inputs.binary, Group1Var0)
  val binaryHas = new Requiring(s8.s813.Procedure81322, OptionCondition(Inputs.binary.reads))
  val binaryConfigurable =
    new Configurable(Inputs.binary, "binary inputs", PluginSteps.uninstallBinaryInputs, Group1Var0, Sequence.one)

  val doubleBitLevelOne = new LevelOneWithoutReads(Inputs.doubleBit, Group3Var0)
  val doubleBitWithout = new WithoutInputs(Inputs.doubleBit, Group3Var0)
  val doubleBitHas = new Requiring(s8.s821.Procedure82121, OptionCondition(Inputs.doubleBit.reads))
  val doubleBitConfigurable = new Configurable(
    Inputs.doubleBit,
    "double-bit inputs",
    PluginSteps.uninstallDoubleBitBinaryInputs,
    Group3Var0,
    Sequence.zero)
}
