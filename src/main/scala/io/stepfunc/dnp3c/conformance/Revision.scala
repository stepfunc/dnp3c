// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import java.nio.file.{ Path, Paths }

import io.stepfunc.dnp3c.conformance.api.{
  ConfigProblem,
  ConfigurationException,
  OptionSource,
  TestElement,
  TestProcedure,
  TestSection,
  Unmet
}
import io.stepfunc.dnp3c.conformance.api.options.{ OutstationOptions, TestOptions }
import io.stepfunc.dnp3c.dnp3.MessagePolicy

/**
 * A revision of the DNP3 IED Certification Procedure: its tree of sections and procedures, with the numbers and titles
 * it gives them, and the subset levels each applies to. A run covers one revision at one subset level, and its results
 * are kept apart from every other revision's and level's.
 *
 * @param id e.g. "2.7", as given to `-rev`
 * @param title the document's title
 * @param date its publication date, as yyyy-mm-dd
 * @param subsetLevels the DNP3 subset levels it tests, and that its elements apply to unless they say otherwise
 * @param policies rules every message a procedure receives must follow, unless a step exempts one
 * @param configurationProblems what the revision needs configured beyond the options' own checks, whatever procedures
 *   a run selects; each procedure adds its own
 */
final class Revision private[conformance] (
    val id: String,
    val title: String,
    val date: String,
    val subsetLevels: List[Int],
    val sections: List[TestSection],
    val policies: List[MessagePolicy] = Nil,
    configurationProblems: OptionSource => List[ConfigProblem] = _ => Nil) {

  /** every procedure, for running or describing everything with "all" */
  val all: TestSection = new TestSection {
    override def id: String = "all"
    override def description: String = "All procedures"
    override def sections: Seq[TestSection] = Revision.this.sections
  }

  /** every section and procedure by id, and "all" */
  val elements: Map[String, TestElement] = index(sections)

  // every element's subset levels: its own, or else its section's, or else the revision's
  private val levels: Map[String, Set[Int]] = {
    def recurse(map: Map[String, Set[Int]], e: TestElement, inherited: Set[Int]): Map[String, Set[Int]] = {
      val own = e.levels.getOrElse(inherited)
      e.subElements.foldLeft(map + (e.id -> own))((m, child) => recurse(m, child, own))
    }
    sections.foldLeft(Map("all" -> subsetLevels.toSet))((m, s) => recurse(m, s, subsetLevels.toSet))
  }

  /** the subset levels a procedure or section of this revision applies to */
  def levelsOf(element: TestElement): Set[Int] =
    levels.getOrElse(element.id, element.levels.getOrElse(subsetLevels.toSet))

  /**
   * The subset level the configuration says the device is configured for
   *
   * @throws ConfigurationException if the configuration doesn't set it, or this revision doesn't test that level
   */
  def subsetLevel(options: OptionSource): Int = {
    val option = OutstationOptions.subsetLevel
    def problem(value: String, message: String) =
      new ConfigurationException("configuration", List(ConfigProblem(option.id, value, message)))
    options.get(option) match {
      case None =>
        throw problem("", s"required: the subset level the device is configured for (revision $id tests ${levelList})")
      case Some(level) if !subsetLevels.contains(level) =>
        throw problem(level.toString, s"revision $id tests ${levelList} only")
      case Some(level) => level
    }
  }

  /**
   * Why a procedure won't run on a device configured for a subset level, if it won't. The runner, the configuration
   * check and `-disabled` all decide with it.
   */
  def skip(procedure: TestProcedure, options: OptionSource, level: Int): Option[Skip] =
    if (procedure.incomplete) Some(Skip.NotImplemented)
    else {
      val levels = levelsOf(procedure)
      if (!levels.contains(level)) Some(Skip.NotApplicable(levels.toList.sorted))
      else procedure.disabledBy(options).map(Skip.Disabled(_))
    }

  /**
   * Check that the configuration is one this revision can test: it gives a subset level the revision tests, and it has
   * what the revision needs configured, whatever procedures run, and what each procedure selected needs, if it will run
   * (see [[skip]]).
   *
   * @param selected the procedures a run will run; all of them unless given
   * @return the subset level
   * @throws ConfigurationException listing every problem
   */
  def validate(options: OptionSource, selected: Iterable[TestProcedure] = flatMapProcedures(Some(_))): Int = {
    val level = subsetLevel(options)
    // what the procedures that will run need, each problem once
    val procedures = selected.filter(skip(_, options, level).isEmpty)
    val problems =
      (configurationProblems(options) ++ procedures.flatMap(_.definition.configurationProblems(options))).distinct
    if (problems.nonEmpty) throw new ConfigurationException("configuration", problems)
    level
  }

  private def levelList: String =
    subsetLevels.map(l => s"level $l").mkString(", ")

  def flatMapProcedures[B](optional: TestProcedure => Option[B]): Iterable[B] = {
    def recurse(e: TestElement): Iterable[B] = e match {
      case p: TestProcedure => optional(p)
      case s => s.subElements.flatMap(recurse)
    }
    sections.flatMap(recurse)
  }

  /** the revision's own folder in the output directory, which holds a folder for each subset level */
  def resultsDirectory(options: OptionSource): Path = Paths.get(options.get(TestOptions.outputDirectory), id)

  /**
   * Where a run's results and report go: the folder for the configuration's subset level, in the revision's folder
   *
   * @throws ConfigurationException as [[subsetLevel]] does
   */
  def runDirectory(options: OptionSource): Path = resultsDirectory(options).resolve(s"L${subsetLevel(options)}")

  override def toString: String = s"$title, revision $id ($date)"

  private def index(sections: List[TestSection]): Map[String, TestElement] = {
    def recurse(map: Map[String, TestElement], elements: Iterable[TestElement]): Map[String, TestElement] =
      elements.foldLeft(map) { (sum, e) =>
        sum.get(e.id).foreach(_ => throw new Exception(s"Duplicate id in revision $id: ${e.id}"))
        recurse(sum + (e.id -> e), e.subElements)
      }
    recurse(Map("all" -> all), sections)
  }
}

/** why a procedure won't run, see [[Revision.skip]] */
enum Skip {

  /** it has no steps yet */
  case NotImplemented

  /** it doesn't apply at the device's subset level, only at these, in order */
  case NotApplicable(levels: List[Int])

  /** the device lacks an optional feature it tests */
  case Disabled(unmet: Unmet)
}

object Revisions {

  /** DNP3 IED Certification Procedure, Subset Level 2, version 2.7 */
  val v2_7: Revision = new Revision(
    "2.7",
    "DNP3 IED Certification Procedure, Subset Level 2",
    "2016-03-02",
    List(2),
    v27.Tree.sections)

  /** DNP3 IED Certification Procedure, Subset Levels 1, 2 and 3, version 3.1 rev 1 */
  val v3_1r1: Revision = new Revision(
    "3.1r1",
    "DNP3 IED Certification Procedure, Subset Levels 1, 2 and 3",
    "2022-09-13",
    List(1, 2, 3),
    v31r1.Tree.sections,
    v31r1.Rules.all,
    v31r1.EventRules.configurationProblems
  )

  /** every revision, oldest first */
  val list: List[Revision] = List(v2_7, v3_1r1)

  def find(id: String): Option[Revision] = list.find(_.id == id)
}
