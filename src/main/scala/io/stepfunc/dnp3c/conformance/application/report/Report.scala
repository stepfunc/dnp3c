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
    } ++ s.sections.flatMap(convert(level + 1, _)) ++ s.procedures.flatMap(convert(level + 1, _))
  }

  private def convert(level: Int, p: Report.Procedure)(implicit i: Indentation): Iterable[String] = {

    def link(text: String) = "<a href=\"./" + p.id + "/" + ResultFiles.log + "\">" + text + "</a>"
    // a procedure that has run has a log, which its id and its result both open
    def id = if (p.incomplete || p.outcome.isEmpty) p.id else link(p.id)
    def pcapLink =
      if (p.hasPcaps &&
        !p.incomplete &&
        p.outcome.exists(o => !o.isInstanceOf[ProcedureOutcome.Skipped])) {
        "<a href=\"./" + p.id + "/" + ResultFiles.capture + "\">pcap</a>"
      } else {
        "----"
      }

    def getText(): String = if (p.incomplete) "----"
    else p.outcome match {
      case Some(ProcedureOutcome.Passed) => link("PASS")
      case Some(ProcedureOutcome.Failed(_)) => link("FAIL")
      case Some(ProcedureOutcome.Skipped(_)) => link("SKIP")
      case None => "not run"
    }

    def getClass(): Clazz = if (p.incomplete) Classes.unimplemented
    else p.outcome match {
      case Some(ProcedureOutcome.Passed) => Classes.pass
      case Some(ProcedureOutcome.Failed(_)) => Classes.fail
      case Some(ProcedureOutcome.Skipped(_)) => Classes.skip
      case None => Classes.noResult
    }

    // why the procedure was skipped, or what it noted about a pass, under its title
    def skipReason: String = p.outcome match {
      case Some(ProcedureOutcome.Skipped(reason)) if reason.nonEmpty =>
        "<br><span class=\"reason\">Skipped: " + Tags.escape(reason) + "</span>"
      case Some(ProcedureOutcome.Passed) =>
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

  private def procedures(s: Report.Section): Iterable[Report.Procedure] =
    s.procedures ++ s.sections.flatMap(procedures)

  // e.g. "146 passed", counting implemented procedures by result
  private def totals: Iterable[(Clazz, String)] = {
    val implemented = sections.flatMap(procedures).filterNot(_.incomplete).toList
    def count(counted: Option[ProcedureOutcome] => Boolean) = implemented.count(p => counted(p.outcome))
    List(
      (Classes.pass, s"${count(_.contains(ProcedureOutcome.Passed))} passed"),
      (Classes.fail, s"${count(_.exists(_.isInstanceOf[ProcedureOutcome.Failed]))} failed"),
      (Classes.skip, s"${count(_.exists(_.isInstanceOf[ProcedureOutcome.Skipped]))} skipped"),
      (Classes.noResult, s"${count(_.isEmpty)} not run")
    )
  }

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

  case class Procedure(
      id: String,
      incomplete: Boolean,
      description: String,
      outcome: Option[ProcedureOutcome],
      hasPcaps: Boolean,
      notes: List[String] = Nil)
  case class Section(id: String, description: String, sections: Iterable[Section], procedures: Iterable[Procedure])

  /**
   * The report of a revision's results at a subset level
   *
   * @param path the folder of the revision's results at the level
   */
  def create(path: Path, revision: Revision, level: Int): Report = {
    val ignored = List.newBuilder[String]

    def convert(section: TestSection): Section =
      Section(section.id, section.description, section.sections.map(convert), section.procedures.map(read))

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
        counted.toList.flatMap(_.notes))
    }

    val sections = revision.sections.map(convert)
    Report(revision, level, sections, ignored.result())
  }

}
