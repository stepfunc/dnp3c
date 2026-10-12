// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import java.nio.file.Files

import io.stepfunc.dnp3c.conformance.api.Options
import io.stepfunc.dnp3c.conformance.api.options.TestOptions
import io.stepfunc.dnp3c.conformance.application.report.Resources
import io.stepfunc.dnp3c.dnp3.DefaultIntegrationPlugin
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class HarnessTest extends AnyFunSuite with Matchers {

  private def output = Files.createTempDirectory("dnp3c").resolve("results")

  private def options(directory: java.nio.file.Path): Options =
    Options.from(Map(TestOptions.outputDirectory.id -> directory.toString))

  test("rejects an unknown id before writing anything") {
    val directory = output
    val harness = new Harness(options(directory), new DefaultIntegrationPlugin())
    assertThrows[IllegalArgumentException](harness.run("9.9"))
    Files.exists(directory) should equal(false)
  }

  test("writes the report and its stylesheet without a plugin") {
    val directory = output
    val report = Harness.writeReport(options(directory))
    report should equal(directory.resolve("report.html"))
    Files.exists(report) should equal(true)
    Files.exists(directory.resolve("report.css")) should equal(true)
  }

  test("rewrites the stylesheet, so an old or edited one doesn't stay") {
    val directory = output
    Files.createDirectories(directory)
    Files.writeString(directory.resolve("report.css"), "td { color: red; }")
    Harness.writeReport(options(directory))
    Files.readString(directory.resolve("report.css")) should equal(Resources.read("report.css"))
  }
}
