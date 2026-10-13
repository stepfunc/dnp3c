// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import java.nio.file.{ Files, Path }

import io.stepfunc.dnp3c.conformance.api.Options
import io.stepfunc.dnp3c.conformance.api.options.{ OutstationOptions, TestOptions }
import io.stepfunc.dnp3c.conformance.application.TestResult
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
    TestResult.write(TestResult.Saved(TestResult.Pass, revision, level, None), folder.resolve("results.properties"))
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

  test("tells about procedure folders in the layout of 0.1.0, and leaves them out") {
    val directory = output
    save(directory.resolve("8.1.2"), None, None)
    Files.createDirectories(directory.resolve("8.3.2"))
    Files.writeString(directory.resolve("8.3.2").resolve("log.html"), "")
    val report = Files.readString(Harness.writeReport(options(directory), revision))
    report should include("holds 2 folders named after 2.7 procedures (e.g. 8.1.2)")
    report should include("0 passed")
  }

  test("other folders in the output directory aren't taken for 0.1.0 results") {
    val directory = output
    save(directory.resolve("backup"), None, None)
    save(directory.resolve("8.1.2"), Some("2.7"))
    Files.createDirectories(directory.resolve("8.3.2"))
    Files.readString(Harness.writeReport(options(directory), revision)) should not include "folders named after"
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
