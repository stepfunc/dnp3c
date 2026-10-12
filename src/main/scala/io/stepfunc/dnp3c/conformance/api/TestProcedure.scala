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

trait TestProcedure extends TestElement {

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

trait TestSection extends TestElement {

  def sections: Iterable[TestSection] = Nil

  def procedures: Iterable[TestProcedure] = Nil

  final override def subElements: Iterable[TestElement] = sections ++ procedures
}

object TestProcedure {

  private def enabledBy(options: List[OptionCondition]): Iterable[LabeledStep] = {

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

  private def disabledBy(cond: OptionCondition): Iterable[LabeledStep] = {
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
