// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api

import io.stepfunc.dnp3c.dnp3.Comment

sealed trait TestElement {

  def id: String

  def description: String

  def subElements: Iterable[TestElement] = Nil
}

class OptionCondition private (val option: BooleanOption, val requiredValue: Boolean) {

  def unary_! : OptionCondition = new OptionCondition(option, !requiredValue)

  def satisfied(source: OptionSource): Boolean = {
    source.get(option) == requiredValue
  }
}

object OptionCondition {

  implicit def convert(booleanOption: BooleanOption): OptionCondition = {
    new OptionCondition(booleanOption, true)
  }

  def apply(option: BooleanOption): OptionCondition = new OptionCondition(option, true)
}

/**
 * What a procedure does: its steps, and the options it needs to run. It has no number or title: those depend on where
 * a revision's tree places it, see [[TestProcedure]]. A definition placed in several trees is shared by them.
 */
trait ProcedureDefinition {

  case class Configuration(disabledBy: Option[OptionCondition], steps: Iterable[LabeledStep])

  def incomplete: Boolean = false

  // require one or more options to be set for test to be run
  protected def requiredOptions: List[OptionCondition] = Nil

  protected def steps(options: OptionSource): Iterable[LabeledStep]

  // the first required option that isn't satisfied, if any; doesn't build the steps
  def disabledBy(options: OptionSource): Option[OptionCondition] = requiredOptions.find(x => !x.satisfied(options))

  def configure(options: OptionSource): Configuration = {

    val disabledOption = disabledBy(options)

    disabledOption match {
      case Some(x) =>
        Configuration(disabledOption, TestProcedure.disabledBy(x))
      case None =>
        Configuration(disabledOption, TestProcedure.enabledBy(requiredOptions) ++ steps(options))
    }
  }

}

/** A procedure as a revision's tree places it: its number and title there, and its definition. */
final class TestProcedure(val id: String, val description: String, val definition: ProcedureDefinition)
    extends TestElement {

  def incomplete: Boolean = definition.incomplete

  def disabledBy(options: OptionSource): Option[OptionCondition] = definition.disabledBy(options)

  def configure(options: OptionSource): definition.Configuration = definition.configure(options)
}

trait TestSection extends TestElement {

  def sections: Iterable[TestSection] = Nil

  def procedures: Iterable[TestProcedure] = Nil

  final override def subElements: Iterable[TestElement] = sections ++ procedures
}

object TestProcedure {

  def apply(id: String, description: String, definition: ProcedureDefinition): TestProcedure =
    new TestProcedure(id, description, definition)

  private[api] def enabledBy(options: List[OptionCondition]): Iterable[LabeledStep] = {

    def step: LabeledStep = {
      LabeledStep(
        "This procedure is enabled because the following options:",
        describe(
          options.map(x => Comment(s"${x.option.id} == ${x.requiredValue}"))
        )
      )
    }

    if (options.isEmpty) Nil else List(step)
  }

  private[api] def disabledBy(cond: OptionCondition): Iterable[LabeledStep] = {
    List(
      LabeledStep(
        "describe disabled options",
        describe(
          s"This procedure was skipped because configuration value ${cond.option.id} != ${cond.requiredValue}",
          "The description of this option is:",
          cond.option.description
        )
      )
    )
  }

}
