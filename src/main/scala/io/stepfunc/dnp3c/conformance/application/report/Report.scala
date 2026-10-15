// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application.report

import java.nio.file.{ Files, Path }

import io.stepfunc.dnp3c.conformance.Revision
import io.stepfunc.dnp3c.conformance.api.{ TestProcedure, TestSection }
import io.stepfunc.dnp3c.conformance.api.ProcedureOutcome
import io.stepfunc.dnp3c.conformance.application.{ ResultFiles, SavedResult }

/**
 * The report of one revision's results at one subset level
 *
 * @param notices results found but not counted, and why, e.g. one saved under another revision
 */
case class Report(revision: Revision, level: Int, sections: Iterable[Report.Section], notices: List[String]) {

  private def convert(level: Int, s: Report.Section)(implicit i: Indentation): Iterable[String] = {
    Tags.tr(Attributes.classes(Classes.section, Classes.level(level))) {
      Tags.td { List(s.id) } ++
        Tags.td(Nil) ++
        Tags.td(Nil) ++
        Tags.td { List(Tags.escape(s.description)) }
    } ++ s.children.flatMap {
      case c: Report.Section => convert(level + 1, c)
      case p: Report.Procedure => convert(level + 1, p)
    }
  }

  private def convert(level: Int, p: Report.Procedure)(implicit i: Indentation): Iterable[String] = {

    def link(text: String) = "<a href=\"./" + p.id + "/" + ResultFiles.log + "\">" + text + "</a>"
    // a procedure that has run has a log, which its id and its result both open
    def id = if (p.incomplete || p.outcome.isEmpty) p.id else link(p.id)
    // a procedure that didn't run has no traffic
    def pcapLink =
      if (p.hasPcaps &&
        !p.incomplete &&
        p.outcome.exists(o =>
          !o.isInstanceOf[ProcedureOutcome.Skipped] && !o.isInstanceOf[ProcedureOutcome.Excluded])) {
        "<a href=\"./" + p.id + "/" + ResultFiles.capture + "\">pcap</a>"
      } else {
        "----"
      }

    def getText(): String = if (p.incomplete) "Not implemented"
    else p.outcome match {
      case Some(ProcedureOutcome.Passed) => link("PASS")
      case Some(ProcedureOutcome.Failed(_)) => link("FAIL")
      case Some(ProcedureOutcome.Skipped(_)) => link("SKIP")
      case Some(ProcedureOutcome.Excluded(_)) => link("NOT RUN — EXCLUDED")
      case None => "Not run — no saved result"
    }

    def getClass(): Clazz = if (p.incomplete) Classes.unimplemented
    else p.outcome match {
      case Some(ProcedureOutcome.Passed) => Classes.pass
      case Some(ProcedureOutcome.Failed(_)) => Classes.fail
      case Some(ProcedureOutcome.Skipped(_)) => Classes.skip
      case Some(ProcedureOutcome.Excluded(_)) => Classes.excluded
      case None => Classes.noResult
    }

    // why the procedure was skipped or excluded, or what it warned of and noted about a pass, under its title
    def skipReason: String = p.outcome match {
      case Some(ProcedureOutcome.Skipped(reason)) if reason.nonEmpty =>
        "<br><span class=\"reason\">Skipped: " + Tags.escape(reason) + "</span>"
      case Some(ProcedureOutcome.Excluded(reason)) =>
        "<br><span class=\"exclusion\">Excluded: " + Tags.escape(reason) + "</span>"
      case Some(ProcedureOutcome.Passed) =>
        p.warnings.map(w => "<br><span class=\"result-warning\">⚠ Warning: " + Tags.escape(w) + "</span>").mkString +
          p.notes.map(n => "<br><span class=\"note\">Note: " + Tags.escape(n) + "</span>").mkString
      case _ => ""
    }

    val classes = {
      val base = List(Classes.procedure, Classes.level(level))
      if (p.incomplete) Classes.incomplete :: base else base
    }

    Tags.tr(Attributes.classes(classes)) {
      Tags.td { List(id) } ++
        Tags.td(Attributes.classes(getClass())) {
          List(getText())
        } ++
        Tags.td(List(pcapLink)) ++
        Tags.td(List(Tags.escape(p.description) + skipReason))
    }
  }

  private def procedures(s: Report.Section): Iterable[Report.Procedure] = s.children.flatMap {
    case c: Report.Section => procedures(c)
    case p: Report.Procedure => List(p)
  }

  private def implemented: List[Report.Procedure] = sections.flatMap(procedures).filterNot(_.incomplete).toList

  private def excluded: Int = implemented.count(_.outcome.exists(_.isInstanceOf[ProcedureOutcome.Excluded]))

  // e.g. "146 passed", counting implemented procedures by result: each in one count, so they add up to the total, with
  // a pass that has warnings counted apart from a clean pass
  private def totals: Iterable[(Clazz, String)] = {
    def count(counted: Option[ProcedureOutcome] => Boolean) = implemented.count(p => counted(p.outcome))
    def passed(withWarnings: Boolean) =
      implemented.count(p => p.outcome.contains(ProcedureOutcome.Passed) && p.warnings.nonEmpty == withWarnings)
    List(
      (Classes.pass, s"${passed(withWarnings = false)} passed"),
      (Classes.warning, s"${passed(withWarnings = true)} passed with warnings"),
      (Classes.fail, s"${count(_.exists(_.isInstanceOf[ProcedureOutcome.Failed]))} failed"),
      (Classes.skip, s"${count(_.exists(_.isInstanceOf[ProcedureOutcome.Skipped]))} skipped"),
      (Classes.excluded, s"$excluded excluded"),
      (Classes.noResult, s"${count(_.isEmpty)} with no saved result")
    )
  }

  // a report with exclusions mustn't read as a clean result
  private def exclusionWarning(implicit i: Indentation): Iterable[String] =
    if (excluded == 0) Nil
    else
      Tags.p(Attributes.classes(Clazz("exclusions")))(List(
        s"<strong>${Tags.escape(Report.exclusionWarning(excluded))}</strong>",
        "<br>A skipped procedure doesn't apply to the device or its configuration, and the device can still conform. " +
          "An excluded procedure does apply, but the configuration left it out of the run (test.exclude)."
      ))

  def toHTML(deviceUnderTest: String): Iterable[String] = {

    def headings(implicit i: Indentation): Iterable[String] = List("id", "result", "capture", "description").flatMap {
      s =>
        Tags.th(List(s))
    }

    implicit val indent = new Indentation()

    val generated =
      java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z"))

    Branding.page(
      "DNP3 Conformance Report",
      ResultFiles.stylesheet,
      deviceUnderTest,
      Tags.h1(List("DNP3 Conformance Report")) ++
        Tags.p(Attributes.classes(Clazz("revision")))(
          List(Tags.escape(s"${revision.title}, revision ${revision.id} (${revision.date}), subset level $level"))) ++
        Tags.p(List(s"Generated $generated")),
      totals.flatMap { case (clazz, text) => Tags.span(Attributes.classes(Clazz("total"), clazz))(List(text)) }
    ) {
      exclusionWarning ++
        notices.flatMap(n => Tags.p(Attributes.classes(Clazz("notice")))(List(Tags.escape(n)))) ++
        Tags.p(Attributes.classes(Clazz("hint")))(List("Click a procedure's id or result to open its log.")) ++
        Tags.table(Attributes.classes(Clazz("results"))) {
          Tags.tr(headings) ++
            sections.flatMap(s => convert(0, s))
        }
    }
  }

}

object Report {

  // what the report, and the end of a run, say of the procedures excluded
  def exclusionWarning(count: Int): String =
    if (count == 1) "1 procedure was excluded and not run. Conformance has not been demonstrated for it."
    else s"$count procedures were excluded and not run. Conformance has not been demonstrated for them."

  sealed trait Element

  case class Procedure(
      id: String,
      incomplete: Boolean,
      description: String,
      outcome: Option[ProcedureOutcome],
      hasPcaps: Boolean,
      notes: List[String] = Nil,
      warnings: List[String] = Nil)
      extends Element

  /** @param children its sections and procedures, in the tree's order */
  case class Section(id: String, description: String, children: Iterable[Element]) extends Element

  /**
   * The report of a revision's results at a subset level
   *
   * @param path the folder of the revision's results at the level
   */
  def create(path: Path, revision: Revision, level: Int): Report = {
    val ignored = List.newBuilder[String]

    def convert(section: TestSection): Section =
      Section(
        section.id,
        section.description,
        section.subElements.collect {
          case s: TestSection => convert(s)
          case p: TestProcedure => read(p)
        })

    def read(procedure: TestProcedure): Procedure = {
      val folder = path.resolve(procedure.id)
      val file = folder.resolve(ResultFiles.result).toFile
      val saved = if (file.exists()) Some(SavedResult.read(file)) else None
      // a result saved under another revision or level, or before results recorded them, isn't this run's
      def matches(s: SavedResult) = s.revision.contains(revision.id) && s.level.contains(level)
      val counted = saved.filter(matches)
      saved.filterNot(matches).foreach { s =>
        val when = (s.revision, s.level) match {
          case (Some(r), Some(l)) => s"under revision $r at subset level $l"
          case (Some(r), None) => s"under revision $r before results recorded their subset level"
          case _ => "before results recorded their revision"
        }
        ignored += s"The result in ${procedure.id}/ was saved $when, so it isn't counted. Run the procedure again."
      }
      // a run that was cut short leaves a log, or a capture, but no result
      if (saved.isEmpty &&
        List(ResultFiles.log, ResultFiles.capture).exists(f => Files.isRegularFile(folder.resolve(f))))
        ignored += s"${procedure.id}/ has a log or capture but no result: its last run didn't finish. Run it again."
      val hasPcaps = folder.resolve(ResultFiles.capture).toFile.exists()
      Procedure(
        procedure.id,
        procedure.incomplete,
        procedure.description,
        counted.map(_.outcome),
        hasPcaps,
        counted.toList.flatMap(_.notes),
        counted.toList.flatMap(_.warnings)
      )
    }

    val sections = revision.sections.map(convert)
    Report(revision, level, sections, ignored.result())
  }

}
