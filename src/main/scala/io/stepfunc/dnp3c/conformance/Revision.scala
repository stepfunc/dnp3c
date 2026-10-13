// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import java.nio.file.{ Path, Paths }

import io.stepfunc.dnp3c.conformance.api.{ OptionSource, TestElement, TestProcedure, TestSection }
import io.stepfunc.dnp3c.conformance.api.options.TestOptions

/**
 * A revision of the DNP3 IED Certification Procedure: its tree of sections and procedures, with the numbers and titles
 * it gives them. A run covers one revision, and its results are kept apart from every other revision's.
 *
 * @param id e.g. "2.7", as given to `-rev`
 * @param title the document's title
 * @param date its publication date, as yyyy-mm-dd
 * @param subsetLevels the DNP3 subset levels it tests
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

  def flatMapProcedures[B](optional: TestProcedure => Option[B]): Iterable[B] = {
    def recurse(section: TestSection): Iterable[B] =
      section.procedures.flatMap(x => optional(x)) ++ section.sections.flatMap(s => recurse(s))
    sections.flatMap(s => recurse(s))
  }

  /** the revision's own folder in the output directory, where its results and report go */
  def resultsDirectory(options: OptionSource): Path = Paths.get(options.get(TestOptions.outputDirectory), id)

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
