// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api

import io.stepfunc.dnp3c.dnp3.Comment

sealed trait TestElement {

  def id: String

  def description: String

  // the subset levels the element applies to, if it says; otherwise those of the section it's in, and at the top, those
  // of the revision
  def levels: Option[Set[Int]] = None

  def subElements: Iterable[TestElement] = Nil
}

/**
 * What a procedure needs of the device's configuration in order to run. When it isn't met, the procedure is skipped:
 * the device lacks an optional feature the procedure tests. Requirements combine with [[Requirement.all]],
 * [[Requirement.anyOf]] and [[Requirement.not]]; the simplest is an [[OptionCondition]].
 */
sealed trait Requirement {

  /** why the configuration doesn't meet it, if it doesn't */
  def unmet(options: OptionSource): Option[Unmet]

  final def satisfied(options: OptionSource): Boolean = unmet(options).isEmpty

  /** the option conditions it's made of */
  def conditions: List[OptionCondition]

  /** e.g. "supports.a == true and (supports.b == true or supports.c == true)" */
  def describe: String

  // describe, in parentheses when it combines others, to nest in a combination
  private[api] def nested: String = describe
}

/**
 * A requirement the configuration doesn't meet
 *
 * @param requirement the part that isn't met: for one that needs all of several, the first of them that isn't
 * @param values what the configuration gives each option the requirement involves
 */
case class Unmet(requirement: Requirement, values: List[(BooleanOption, Boolean)]) {

  /** e.g. "supports.a != true: the description of supports.a" */
  def message: String = requirement match {
    case c: OptionCondition => s"${c.option.id} != ${c.requiredValue}: ${c.option.description}"
    case r => s"it needs ${r.describe}"
  }
}

class OptionCondition private (val option: BooleanOption, val requiredValue: Boolean) extends Requirement {

  def unary_! : OptionCondition = new OptionCondition(option, !requiredValue)

  def unmet(options: OptionSource): Option[Unmet] = {
    val value = options.get(option)
    Option.when(value != requiredValue)(Unmet(this, List(option -> value)))
  }

  def conditions: List[OptionCondition] = List(this)

  def describe: String = s"${option.id} == $requiredValue"
}

object OptionCondition {

  implicit def convert(booleanOption: BooleanOption): OptionCondition = {
    new OptionCondition(booleanOption, true)
  }

  def apply(option: BooleanOption): OptionCondition = new OptionCondition(option, true)
}

object Requirement {

  /** nothing: the procedure always runs */
  val none: Requirement = all()

  /** every one of them; the first that isn't met is the reason */
  def all(requirements: Requirement*): Requirement = All(requirements.toList)

  /** at least one of them, of which there must be some */
  def anyOf(requirements: Requirement*): Requirement = {
    require(requirements.nonEmpty, "anyOf needs at least one requirement")
    AnyOf(requirements.toList)
  }

  /** that the requirement isn't met */
  def not(requirement: Requirement): Requirement = Not(requirement)

  private def values(r: Requirement, options: OptionSource): List[(BooleanOption, Boolean)] =
    r.conditions.map(_.option).distinct.map(o => o -> options.get(o))

  private case class All(requirements: List[Requirement]) extends Requirement {
    def unmet(options: OptionSource): Option[Unmet] = requirements.iterator.flatMap(_.unmet(options)).nextOption()
    def conditions: List[OptionCondition] = requirements.flatMap(_.conditions)
    def describe: String = requirements.map(_.nested).mkString(" and ")
    override private[api] def nested: String = if (requirements.size > 1) s"($describe)" else describe
  }

  private case class AnyOf(requirements: List[Requirement]) extends Requirement {
    def unmet(options: OptionSource): Option[Unmet] =
      Option.when(requirements.forall(!_.satisfied(options)))(
        Unmet(this, values(this, options)))
    def conditions: List[OptionCondition] = requirements.flatMap(_.conditions)
    def describe: String = requirements.map(_.nested).mkString(" or ")
    override private[api] def nested: String = if (requirements.size > 1) s"($describe)" else describe
  }

  private case class Not(requirement: Requirement) extends Requirement {
    def unmet(options: OptionSource): Option[Unmet] =
      Option.when(requirement.satisfied(options))(Unmet(this, values(this, options)))
    def conditions: List[OptionCondition] = requirement.conditions
    def describe: String = s"not ${requirement.nested}"
  }
}

/**
 * What a procedure does: its steps, and the options it needs to run. It has no number or title: those depend on where
 * a revision's tree places it, see [[TestProcedure]]. A definition placed in several trees is shared by them.
 */
trait ProcedureDefinition {

  /**
   * @param cleanup steps that restore the DUT once the procedure ends, however it ends: the runner runs them after the
   *   steps and their deferred checks, or after a failure
   */
  case class Configuration(
      disabledBy: Option[Unmet],
      steps: Iterable[LabeledStep],
      cleanup: Iterable[LabeledStep] = Nil)

  def incomplete: Boolean = false

  // options that must all be set for the procedure to run: the usual requirement
  protected def requiredOptions: List[OptionCondition] = Nil

  // what the procedure needs to run; override it for anything other than all of requiredOptions
  protected def requirement: Requirement = Requirement.all(requiredOptions*)

  protected def steps(options: OptionSource): Iterable[LabeledStep]

  // steps that restore the DUT once the procedure ends, even if it fails: see Configuration.cleanup
  protected def cleanup(options: OptionSource): Iterable[LabeledStep] = Nil

  // why the procedure won't run, if it won't; doesn't build the steps
  def disabledBy(options: OptionSource): Option[Unmet] = requirement.unmet(options)

  def configure(options: OptionSource): Configuration = {

    val disabledOption = disabledBy(options)

    disabledOption match {
      case Some(x) =>
        Configuration(disabledOption, TestProcedure.disabledBy(x))
      case None =>
        Configuration(disabledOption, TestProcedure.enabledBy(requirement) ++ steps(options), cleanup(options))
    }
  }

}

/**
 * A procedure as a revision's tree places it: its number and title there, its definition, the subset levels it
 * applies to if they differ from its section's, and the levels at which it's mandatory.
 *
 * @param mandatoryAt the subset levels at which the device must meet the definition's requirement: there, a device
 *                    that doesn't has an invalid configuration, rather than lacking an optional feature
 */
final class TestProcedure(
    val id: String,
    val description: String,
    val definition: ProcedureDefinition,
    override val levels: Option[Set[Int]] = None,
    val mandatoryAt: Set[Int] = Set.empty)
    extends TestElement {

  def incomplete: Boolean = definition.incomplete

  def disabledBy(options: OptionSource): Option[Unmet] = definition.disabledBy(options)

  def configure(options: OptionSource): definition.Configuration = definition.configure(options)
}

trait TestSection extends TestElement {

  def sections: Iterable[TestSection] = Nil

  def procedures: Iterable[TestProcedure] = Nil

  final override def subElements: Iterable[TestElement] = sections ++ procedures
}

object TestProcedure {

  def apply(
      id: String,
      description: String,
      definition: ProcedureDefinition,
      levels: Option[Set[Int]] = None,
      mandatoryAt: Set[Int] = Set.empty): TestProcedure =
    new TestProcedure(id, description, definition, levels, mandatoryAt)

  private[api] def enabledBy(requirement: Requirement): Iterable[LabeledStep] = {

    // one line for each option a procedure needs; a combination, on a line of its own
    val lines =
      if (requirement.conditions.isEmpty) Nil
      else if (requirement.describe == requirement.conditions.map(_.describe).mkString(" and "))
        requirement.conditions.map(c => Comment(c.describe))
      else List(Comment(requirement.describe))

    if (lines.isEmpty) Nil
    else List(LabeledStep("This procedure is enabled because the following options:", describe(lines)))
  }

  private[api] def disabledBy(unmet: Unmet): Iterable[LabeledStep] = {
    val lines = unmet.requirement match {
      case cond: OptionCondition =>
        List(
          s"This procedure was skipped because configuration value ${cond.option.id} != ${cond.requiredValue}",
          "The description of this option is:",
          cond.option.description
        )
      case r =>
        s"This procedure was skipped because it needs ${r.describe}, and the configuration has:" ::
          unmet.values.map((o, v) => s"${o.id} == $v: ${o.description}")
    }
    List(LabeledStep("describe disabled options", describe(lines*)))
  }

}
