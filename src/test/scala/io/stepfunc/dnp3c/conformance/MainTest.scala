// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import java.io.File
import java.nio.file.Files

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
      report: Boolean = false) =
    Console.Configuration(
      run = None,
      help = false,
      describe = describe,
      listDisabled = false,
      report = report,
      options = false,
      adapters = false,
      writeConfigFile = writeConfigFile,
      config = config)

  private def describe(id: String): Int = {
    val config = Files.createTempFile("dnp3c", ".properties").toFile
    try Main.execute(command(Some(id), None, Some(config)), new DefaultIntegrationPlugin())
    finally config.delete()
  }

  test("describes a procedure") {
    describe("8.1.2") should equal(0)
  }

  test("an unknown id is an error, not an exception") {
    describe("9.9") should not equal 0
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
    withConfig("test.deviceUnderTest = \\uZZZZ\n")(config =>
      Main.execute(command(Some("8.1.2"), None, Some(config)), new DefaultIntegrationPlugin())) should not equal 0
  }

  test("a report that can't be written is an error, not an exception") {
    // the output directory is a file
    val file = Files.createTempFile("dnp3c", ".results")
    try
      withConfig(s"test.output.directory = ${file.toString.replace("\\", "/")}\n")(config =>
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
}
