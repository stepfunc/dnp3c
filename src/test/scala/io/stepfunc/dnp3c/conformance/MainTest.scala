// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import java.io.File
import java.nio.file.Files

import io.stepfunc.dnp3c.conformance.api.{ BooleanOption, OptionGroups, Options }
import io.stepfunc.dnp3c.conformance.api.options.{ OutstationOptions, SupportOptions }
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
      revision: Option[String] = Some("2.7")) =
    Console.Configuration(
      revision = revision,
      listRevisions = false,
      run = None,
      help = false,
      describe = describe,
      listDisabled = false,
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
}
