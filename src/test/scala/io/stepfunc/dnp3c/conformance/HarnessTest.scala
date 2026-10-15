// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import java.nio.file.{ Files, Path }

import io.stepfunc.dnp3c.conformance.api.Options
import io.stepfunc.dnp3c.conformance.api.options.{ OutstationOptions, TestOptions }
import io.stepfunc.dnp3c.conformance.api.ProcedureOutcome
import io.stepfunc.dnp3c.conformance.application.SavedResult
import io.stepfunc.dnp3c.conformance.application.report.Resources
import io.stepfunc.dnp3c.dnp3.DefaultIntegrationPlugin
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class HarnessTest extends AnyFunSuite with Matchers {

  private val revision = Revisions.v2_7

  private def output = Files.createTempDirectory("dnp3c").resolve("results")

  private def options(directory: Path): Options =
    Options.from(Map(TestOptions.outputDirectory.id -> directory.toString, OutstationOptions.subsetLevel.id -> "2"))

  private def save(folder: Path, revision: Option[String], level: Option[Int] = Some(2)): Unit = {
    Files.createDirectories(folder)
    SavedResult.write(SavedResult(ProcedureOutcome.Passed, revision, level), folder.resolve("results.properties"))
  }

  test("rejects an unknown id before writing anything") {
    val directory = output
    val harness = new Harness(options(directory), new DefaultIntegrationPlugin(), revision)
    assertThrows[IllegalArgumentException](harness.run("9.9"))
    Files.exists(directory) should equal(false)
  }

  test("writes the report and its stylesheet in the folder for the revision and level, without a plugin") {
    val directory = output
    val report = Harness.writeReport(options(directory), revision)
    report should equal(directory.resolve("2.7").resolve("L2").resolve("report.html"))
    Files.exists(report) should equal(true)
    Files.exists(directory.resolve("2.7").resolve("L2").resolve("report.css")) should equal(true)
  }

  test("the report names its revision and level") {
    val report = Files.readString(Harness.writeReport(options(output), revision))
    report should include(s"${revision.title}, revision 2.7 (2016-03-02), subset level 2")
  }

  test("a run needs a subset level the revision tests, checked before anything is written") {
    for (settings <- List(Map(), Map(OutstationOptions.subsetLevel.id -> "3"))) {
      val directory = output
      val o = Options.from(Map(TestOptions.outputDirectory.id -> directory.toString) ++ settings)
      assertThrows[api.ConfigurationException](new Harness(o, new DefaultIntegrationPlugin(), revision).run("8.1.2"))
      assertThrows[api.ConfigurationException](Harness.writeReport(o, revision))
      Files.exists(directory) should equal(false)
    }
  }

  test("rewrites the stylesheet, so an old or edited one doesn't stay") {
    val directory = output
    val folder = directory.resolve("2.7").resolve("L2")
    Files.createDirectories(folder)
    Files.writeString(folder.resolve("report.css"), "td { color: red; }")
    Harness.writeReport(options(directory), revision)
    Files.readString(folder.resolve("report.css")) should equal(Resources.read("report.css"))
  }

  test("counts only results saved under the revision and level, and says which it ignored") {
    val directory = output
    val folder = directory.resolve("2.7").resolve("L2")
    save(folder.resolve("8.1.2"), Some("2.7"))
    save(folder.resolve("8.3.2"), Some("3.1r1"))
    save(folder.resolve("8.4.1.2.1"), None, None)
    save(folder.resolve("8.4.1.2.2"), Some("2.7"), Some(3))
    val report = Files.readString(Harness.writeReport(options(directory), revision))
    report should include("1 passed")
    report should include("The result in 8.3.2/ was saved under revision 3.1r1 at subset level 2")
    report should include("The result in 8.4.1.2.1/ was saved before results recorded their revision")
    report should include("The result in 8.4.1.2.2/ was saved under revision 2.7 at subset level 3")
  }

  test("the report shows what a procedure noted about a pass") {
    val directory = output
    val folder = directory.resolve("2.7").resolve("L2").resolve("8.1.2")
    Files.createDirectories(folder)
    SavedResult.write(
      SavedResult(ProcedureOutcome.Passed, Some("2.7"), Some(2), List("compliant with 1815-2012 only")),
      folder.resolve("results.properties"))
    Files.readString(Harness.writeReport(options(directory), revision)) should include(
      "Note: compliant with 1815-2012 only")
  }

  test("the report shows a pass's warnings before its notes, and counts it apart from the clean passes") {
    val directory = output
    val folder = directory.resolve("2.7").resolve("L2")
    def result(id: String, saved: SavedResult): Unit = {
      Files.createDirectories(folder.resolve(id))
      SavedResult.write(saved, folder.resolve(id).resolve("results.properties"))
    }
    def plain(outcome: ProcedureOutcome) = SavedResult(outcome, Some("2.7"), Some(2))
    result("8.1.2", plain(ProcedureOutcome.Passed))
    result(
      "8.3.2",
      SavedResult(
        ProcedureOutcome.Passed,
        Some("2.7"),
        Some(2),
        notes = List("an allowed answer"),
        warnings = List("complies with <1815-2012> & not 202x", "a second warning")
      )
    )
    result("8.4.1.2.1", plain(ProcedureOutcome.Skipped("disabled")))
    result("8.4.1.2.2", plain(ProcedureOutcome.Failed("no response")))
    result("6.1.2", plain(ProcedureOutcome.Excluded("a known gap")))
    val report = Files.readString(Harness.writeReport(options(directory), revision))

    val warning = report.indexOf("⚠ Warning: complies with &lt;1815-2012&gt; &amp; not 202x")
    warning should be >= 0
    report should not include "<1815-2012>"
    report.indexOf("⚠ Warning: a second warning") should be > warning
    report.indexOf("Note: an allowed answer") should be > report.indexOf("⚠ Warning: a second warning")
    report should include("""<span class="warning">⚠ Warning: """)

    // each implemented procedure in exactly one count
    val totals = """<span class="total ([a-z-]+)">\s*(\d+) ([^<]*?)\s*</span>""".r
      .findAllMatchIn(report).map(m => (m.group(1), m.group(3)) -> m.group(2).toInt).toList
    totals.map(_._1) should equal(List(
      ("pass", "passed"),
      ("warning", "passed with warnings"),
      ("fail", "failed"),
      ("skip", "skipped"),
      ("excluded", "excluded"),
      ("no-result", "with no saved result")
    ))
    totals.map(_._2).take(5) should equal(List(1, 1, 1, 1, 1))
    totals.map(_._2).sum should equal(revision.flatMapProcedures(p => Option.when(!p.incomplete)(p)).size)
  }

  test("the report counts no passes with warnings when none has any") {
    val directory = output
    save(directory.resolve("2.7").resolve("L2").resolve("8.1.2"), Some("2.7"))
    val report = Files.readString(Harness.writeReport(options(directory), revision))
    report should include regex """<span class="total pass">\s*1 passed\s*</span>"""
    report should include regex """<span class="total warning">\s*0 passed with warnings\s*</span>"""
    report should not include "⚠"
  }

  test("the report shows each exclusion's reason, counts exclusions apart from skips, and warns of them") {
    val directory = output
    val folder = directory.resolve("2.7").resolve("L2")
    def result(id: String, outcome: ProcedureOutcome): Unit = {
      Files.createDirectories(folder.resolve(id))
      SavedResult.write(SavedResult(outcome, Some("2.7"), Some(2)), folder.resolve(id).resolve("results.properties"))
      Files.writeString(folder.resolve(id).resolve("log.html"), "")
      Files.writeString(folder.resolve(id).resolve("dump.pcap"), "")
    }
    result("8.1.2", ProcedureOutcome.Passed)
    result("8.3.2", ProcedureOutcome.Skipped("disabled because supports.x == false"))
    result("8.4.1.2.1", ProcedureOutcome.Excluded("Assign <b>Class</b> & co aren't supported"))
    result("8.4.1.2.2", ProcedureOutcome.Excluded("a second gap"))
    val report = Files.readString(Harness.writeReport(options(directory), revision))

    report should include("Excluded: Assign &lt;b&gt;Class&lt;/b&gt; &amp; co aren't supported")
    report should not include "<b>Class"
    report should include("Excluded: a second gap")
    report should include("""<a href="./8.4.1.2.1/log.html">NOT RUN — EXCLUDED</a>""")
    for (total <- List("1 passed", "0 failed", "1 skipped", "2 excluded", "with no saved result"))
      report should include(total)
    report should include("2 procedures were excluded and not run. Conformance has not been demonstrated for them.")
    // no traffic for a procedure that didn't run
    report should include("./8.1.2/dump.pcap")
    report should not include "./8.3.2/dump.pcap"
    report should not include "./8.4.1.2.1/dump.pcap"
    // skips as they were, and a procedure without a result, or that isn't implemented, says so
    report should include("""<a href="./8.3.2/log.html">SKIP</a>""")
    report should include("Skipped: disabled because supports.x == false")
    report should include("Not run — no saved result")
    report should include("Not implemented")
  }

  test("a report without exclusions doesn't warn of them") {
    val report = Files.readString(Harness.writeReport(options(output), revision))
    report should include("0 excluded")
    report should not include "excluded and not run"
  }

  test("tells about a procedure whose run left a log but no result") {
    val directory = output
    val folder = directory.resolve("2.7").resolve("L2").resolve("8.1.2")
    Files.createDirectories(folder)
    Files.writeString(folder.resolve("log.html"), "")
    val report = Files.readString(Harness.writeReport(options(directory), revision))
    report should include("8.1.2/ has a log or capture but no result")
    report should include("0 passed")
  }
}
