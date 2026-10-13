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
  TestSection
}
import io.stepfunc.dnp3c.conformance.api.options.{ OutstationOptions, TestOptions }

/**
 * A revision of the DNP3 IED Certification Procedure: its tree of sections and procedures, with the numbers and titles
 * it gives them, and the subset levels each applies to. A run covers one revision at one subset level, and its results
 * are kept apart from every other revision's and level's.
 *
 * @param id e.g. "2.7", as given to `-rev`
 * @param title the document's title
 * @param date its publication date, as yyyy-mm-dd
 * @param subsetLevels the DNP3 subset levels it tests, and that its elements apply to unless they say otherwise
 */
final class Revision private[conformance] (
    val id: String,
    val title: String,
    val date: String,
    val subsetLevels: List[Int],
    val sections: List[TestSection]) {

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

  private def levelList: String =
    subsetLevels.map(l => s"level $l").mkString(", ")

  def flatMapProcedures[B](optional: TestProcedure => Option[B]): Iterable[B] = {
    def recurse(section: TestSection): Iterable[B] =
      section.procedures.flatMap(x => optional(x)) ++ section.sections.flatMap(s => recurse(s))
    sections.flatMap(s => recurse(s))
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

object Revisions {

  /** DNP3 IED Certification Procedure, Subset Level 2, version 2.7 */
  val v2_7: Revision = new Revision(
    "2.7",
    "DNP3 IED Certification Procedure, Subset Level 2",
    "2016-03-02",
    List(2),
    List(s6.Section, s7.Section, s8.Section))

  /** every revision, oldest first */
  val list: List[Revision] = List(v2_7)

  def find(id: String): Option[Revision] = list.find(_.id == id)
}
