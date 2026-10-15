// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import java.io.File
import java.nio.file.Files

import io.stepfunc.dnp3c.conformance.api.{
  BooleanOption, OptionGroups, Options, ProcedureOutcome, ProcedureResult, RunSummary
}
import io.stepfunc.dnp3c.conformance.api.options.{ OutstationOptions, SupportOptions, TestOptions }
import io.stepfunc.dnp3c.conformance.application.SavedResult
import io.stepfunc.dnp3c.conformance.application.Console
import io.stepfunc.dnp3c.dnp3.DefaultIntegrationPlugin
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class MainTest extends AnyFunSuite with Matchers {

  private def command(
      describe: Option[String],
      writeConfigFile: Option[File],
      config: Option[File],
      report: Boolean = false,
      revision: Option[String] = Some("2.7"),
      run: Option[String] = None,
      listDisabled: Boolean = false) =
    Console.Configuration(
      revision = revision,
      listRevisions = false,
      run = run,
      help = false,
      describe = describe,
      listDisabled = listDisabled,
      report = report,
      options = false,
      adapters = false,
      writeConfigFile = writeConfigFile,
      config = config
    )

  private val level2 = "outstation.subset.level = 2\n"

  private def describe(id: String, contents: String = level2): Int =
    withConfig(contents)(config => Main.execute(command(Some(id), None, Some(config)), new DefaultIntegrationPlugin()))

  test("describes a procedure") {
    describe("8.1.2") should equal(0)
  }

  test("an unknown id is an error, not an exception") {
    describe("9.9") should not equal 0
  }

  test("a command that uses procedures needs a subset level the revision tests") {
    describe("8.1.2", contents = "") should not equal 0
    describe("8.1.2", contents = "outstation.subset.level = 3\n") should not equal 0
    describe("8.1.2", contents = "outstation.subset.level = 4\n") should not equal 0
  }

  test("a command that uses procedures needs a known revision") {
    val config = Files.createTempFile("dnp3c", ".properties").toFile
    try {
      Main.execute(command(Some("8.1.2"), None, Some(config), revision = None), new DefaultIntegrationPlugin()) should
        not equal 0
      Main.execute(
        command(Some("8.1.2"), None, Some(config), revision = Some("9.9")),
        new DefaultIntegrationPlugin()) should
        not equal 0
    } finally config.delete()
  }

  test("lists the revisions, and a revision's sections, without a configuration file") {
    Main.execute(Array("-revs"), new DefaultIntegrationPlugin()) should equal(0)
    Main.execute(Array("-help"), new DefaultIntegrationPlugin()) should equal(0)
    Main.execute(Array("-rev", "2.7", "-help"), new DefaultIntegrationPlugin()) should equal(0)
    Main.execute(Array("-rev", "3.1r1", "-help"), new DefaultIntegrationPlugin()) should equal(0)
    Main.execute(Array("-rev", "9.9", "-help"), new DefaultIntegrationPlugin()) should not equal 0
  }

  test("a procedure whose options don't configure it is an error, not an exception") {
    // the default configuration supports device attributes but lists none of them
    describe("8.23.2.1") should not equal 0
  }

  private def withConfig(contents: String)(f: File => Int): Int = {
    val config = Files.createTempFile("dnp3c", ".properties")
    try {
      Files.writeString(config, contents)
      f(config.toFile)
    } finally Files.delete(config)
  }

  test("a configuration file that can't be parsed is an error, not an exception") {
    withConfig(level2 + "test.deviceUnderTest = \\uZZZZ\n")(config =>
      Main.execute(command(Some("8.1.2"), None, Some(config)), new DefaultIntegrationPlugin())) should not equal 0
  }

  test("a report that can't be written is an error, not an exception") {
    // the output directory is a file
    val file = Files.createTempFile("dnp3c", ".results")
    try
      withConfig(level2 + s"test.output.directory = ${file.toString.replace("\\", "/")}\n")(config =>
        Main.execute(command(None, None, Some(config), report = true), new DefaultIntegrationPlugin())) should not equal
        0
    finally Files.delete(file)
  }

  test("a command line that can't be parsed is an error, not an exception") {
    Main.execute(Array("-nonsense"), new DefaultIntegrationPlugin()) should not equal 0
    Main.execute(Array("-run"), new DefaultIntegrationPlugin()) should not equal 0
  }

  test("a configuration file that can't be read or written is an error, not an exception") {
    val missing = new File(Files.createTempDirectory("dnp3c").toFile, "missing/device.properties")
    Main.execute(command(Some("8.1.2"), None, Some(missing)), new DefaultIntegrationPlugin()) should not equal 0
    Main.execute(command(None, Some(missing), None), new DefaultIntegrationPlugin()) should not equal 0
  }

  test("-disabled lists a procedure that doesn't apply at the level once, even if an option disables it too") {
    // 8.26.3 applies at level 3 only, and needs an option that's off by default
    val options = Options.from(Map(OutstationOptions.subsetLevel.id -> "2", SupportOptions.assignNoClass.id -> "false"))
    val lines = Main.disabledLines(options, Revisions.v3_1r1, 2)
    lines.count(_ == "Procedure: 8.26.3") should equal(1)
    lines(lines.indexOf("Procedure: 8.26.3") + 1) should equal("    Not applicable at subset level 2")
  }

  test("-disabled lists each procedure once, whatever the level and options") {
    val booleans = OptionGroups.list.flatMap(_.options).collect { case o: BooleanOption => o.id }
    for {
      revision <- Revisions.list
      level <- revision.subsetLevels
      value <- List(None, Some("true"), Some("false"))
    } {
      val options =
        Options.from(booleans.flatMap(id => value.map(id -> _)).toMap + (OutstationOptions.subsetLevel.id -> s"$level"))
      val ids = Main.disabledLines(options, revision, level).filter(_.startsWith("Procedure: "))
      withClue(s"${revision.id} L$level $value: ")(ids.diff(ids.distinct) should equal(Nil))
    }
  }

  test("-disabled lists an exclusion with its reason, and a procedure that's skipped anyway as skipped") {
    val options = Options.from(Map(
      OutstationOptions.subsetLevel.id -> "2",
      s"${TestOptions.exclude.id}.8.26.2" -> "Assign Class isn't supported yet",
      s"${TestOptions.exclude.id}.8.26.3" -> "nor no-class assignment"
    ))
    val lines = Main.disabledLines(options, Revisions.v3_1r1, 2)
    lines.sliding(2).toList should
      contain(List("Procedure: 8.26.2", "    NOT RUN — EXCLUDED: Assign Class isn't supported yet"))
    lines(lines.indexOf("Procedure: 8.26.3") + 1) should equal("    Not applicable at subset level 2")
    lines.mkString should not include "nor no-class assignment"
  }

  private def withExclusion(id: String, reason: String = "Assign Class isn't supported yet") =
    level2 + s"${TestOptions.exclude.id}.$id = $reason\n"

  private def command31r1(contents: String, describe: Option[String] = None, disabled: Boolean = false): Int =
    withConfig(contents)(config =>
      Main.execute(
        command(describe, None, Some(config), revision = Some("3.1r1"), listDisabled = disabled),
        new DefaultIntegrationPlugin()))

  test("-describe and -disabled show an exclusion, which must name a procedure of the revision") {
    command31r1(withExclusion("8.26.2"), describe = Some("8.26.2")) should equal(0)
    command31r1(withExclusion("8.26.2"), describe = Some("8.26")) should equal(0)
    command31r1(withExclusion("8.26.2"), disabled = true) should equal(0)
    for (id <- List("9.9", "8.26", "all")) {
      command31r1(withExclusion(id), describe = Some("8.1.2")) should not equal 0
      command31r1(withExclusion(id), disabled = true) should not equal 0
    }
    // and give a reason
    command31r1(withExclusion("8.26.2", reason = ""), disabled = true) should not equal 0
    command31r1(level2 + s"${TestOptions.exclude.id} = 8.26.2\n", disabled = true) should not equal 0
  }

  test("a run that excludes a procedure records it, and succeeds") {
    val output = Files.createTempDirectory("dnp3c")
    val contents = withExclusion("8.26.2") +
      s"${TestOptions.outputDirectory.id} = ${output.toString.replace("\\", "/")}\n"
    withConfig(contents)(config =>
      Main.execute(
        command(None, None, Some(config), revision = Some("3.1r1"), run = Some("8.26")),
        new DefaultIntegrationPlugin())) should equal(0)
    val folder = output.resolve("3.1r1").resolve("L2")
    SavedResult.read(folder.resolve("8.26.2").resolve("results.properties").toFile).outcome should equal(
      ProcedureOutcome.Excluded("Assign Class isn't supported yet"))
    Files.readString(folder.resolve("report.html")) should include("Excluded: Assign Class isn't supported yet")
  }

  test("a run's end warns of its exclusions, even when a later procedure failed") {
    val excluded = ProcedureResult("8.26.2", ProcedureOutcome.Excluded("gap"))
    Main.exclusionWarning(RunSummary(List(ProcedureResult("8.1.2", ProcedureOutcome.Passed)), Nil)) should equal(None)
    Main.exclusionWarning(
      RunSummary(List(excluded, ProcedureResult("8.26.3", ProcedureOutcome.Failed("x"))), List("9.1"))) should equal(
      Some("1 procedure(s) excluded and not run. Conformance has not been demonstrated for them."))
  }
}
