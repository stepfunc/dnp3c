// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application.report

import java.nio.file.Path

import io.stepfunc.dnp3c.conformance.api.{ TestProcedure, TestSection }
import io.stepfunc.dnp3c.conformance.application.TestResult

case class Report(sections: Iterable[Report.Section]) {

  private def convert(level: Int, s: Report.Section)(implicit i: Indentation): Iterable[String] = {
    Tags.tr(Attributes.classes(Classes.section, Classes.level(level))) {
      Tags.td { List(s.id) } ++
        Tags.td(Nil) ++
        Tags.td(Nil) ++
        Tags.td { List(Tags.escape(s.description)) }
    } ++ s.sections.flatMap(convert(level + 1, _)) ++ s.procedures.flatMap(convert(level + 1, _))
  }

  private def convert(level: Int, p: Report.Procedure)(implicit i: Indentation): Iterable[String] = {

    def link(text: String) = "<a href=\"./" + p.id + "/log.html\">" + text + "</a>"
    // a procedure that has run has a log, which its id and its result both open
    def id = if (p.incomplete || p.result.isEmpty) p.id else link(p.id)
    def pcapLink =
      if (p.hasPcaps &&
        !p.incomplete &&
        (p.result.contains(TestResult.Pass) || p.result.contains(TestResult.Fail))) {
        "<a href=\"./" + p.id + "/dump.pcap\">pcap</a>"
      } else {
        "----"
      }

    def getText(): String = if (p.incomplete) "----"
    else p.result match {
      case Some(TestResult.Pass) => link("PASS")
      case Some(TestResult.Fail) => link("FAIL")
      case Some(TestResult.Skip) => link("SKIP")
      case None => "not run"
    }

    def getClass(): Clazz = if (p.incomplete) Classes.unimplemented
    else p.result match {
      case Some(TestResult.Pass) => Classes.pass
      case Some(TestResult.Fail) => Classes.fail
      case Some(TestResult.Skip) => Classes.skip
      case None => Classes.noResult
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
        Tags.td(List(Tags.escape(p.description)))
    }
  }

  private def procedures(s: Report.Section): Iterable[Report.Procedure] =
    s.procedures ++ s.sections.flatMap(procedures)

  // e.g. "146 passed", counting implemented procedures by result
  private def totals: Iterable[(Clazz, String)] = {
    val implemented = sections.flatMap(procedures).filterNot(_.incomplete).toList
    def count(result: Option[TestResult]) = implemented.count(_.result == result)
    List(
      (Classes.pass, s"${count(Some(TestResult.Pass))} passed"),
      (Classes.fail, s"${count(Some(TestResult.Fail))} failed"),
      (Classes.skip, s"${count(Some(TestResult.Skip))} skipped"),
      (Classes.noResult, s"${count(None)} not run")
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
      "report.css",
      deviceUnderTest,
      Tags.h1(List("DNP3 Conformance Report")) ++ Tags.p(List(s"Generated $generated")),
      totals.flatMap { case (clazz, text) => Tags.span(Attributes.classes(Clazz("total"), clazz))(List(text)) }
    ) {
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
      result: Option[TestResult],
      hasPcaps: Boolean)
  case class Section(id: String, description: String, sections: Iterable[Section], procedures: Iterable[Procedure])

  def create(path: Path, sections: Iterable[TestSection]): Report = {
    Report(gather(path, sections))
  }

  private def gather(path: Path, sections: Iterable[TestSection]): Iterable[Section] = {

    def convert(section: TestSection): Section = {
      val sections = gather(path, section.sections)
      val procedures = section.procedures.map(p => read(p))
      Section(section.id, section.description, sections, procedures)
    }

    def read(procedure: TestProcedure): Procedure = {
      val folder = path.resolve(procedure.id)
      val file = folder.resolve("results.properties").toFile
      val results = if (file.exists()) Some(TestResult.read(file)) else None
      val hasPcaps = folder.resolve("dump.pcap").toFile.exists()
      Procedure(procedure.id, procedure.incomplete, procedure.description, results, hasPcaps)
    }

    sections.map(s => convert(s))
  }

}
