// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application

import java.io.File

import org.apache.commons.cli.{ CommandLine, DefaultParser, Options }
import org.apache.commons.cli.help.HelpFormatter

object Console {

  def printHelp(): Unit = {
    HelpFormatter
      .builder()
      .setShowSince(false)
      .get()
      .printHelp("dnp3c", "", apacheOptions, "", true)
  }

  def readConfiguration(args: Array[String]): Configuration = {
    val parser = new DefaultParser
    val cmd = parser.parse(apacheOptions, args)
    new Configuration(cmd)
  }

  case class Configuration(
      run: Option[String],
      help: Boolean,
      describe: Option[String],
      listDisabled: Boolean,
      report: Boolean,
      options: Boolean,
      adapters: Boolean,
      writeConfigFile: Option[File],
      config: Option[File]) {

    def this(cmd: CommandLine) = this(
      Console.run.getOptional(cmd),
      Console.help.isPresent(cmd),
      Console.describe.getOptional(cmd),
      Console.disabled.isPresent(cmd),
      Console.report.isPresent(cmd),
      Console.options.isPresent(cmd),
      Console.adapters.isPresent(cmd),
      Console.writeConfig.getOptional(cmd).map(x => new File(x)),
      Console.config.getOptional(cmd).map(x => new File(x))
    )

  }

  private case class OptionDefinition(id: String, hasArgument: Boolean, description: String) {
    def isPresent(cmd: CommandLine): Boolean = cmd.hasOption(id)
    def getOptional(cmd: CommandLine): Option[String] = Option(cmd.getOptionValue(id))
  }

  private val run = OptionDefinition("run", true, "Run the specified test section/procedure")
  private val help = OptionDefinition("help", false, "Print this help information")
  private val describe =
    OptionDefinition("describe", true, "Describe the steps to be executed in the specified procedure")
  private val disabled =
    OptionDefinition("disabled", false, "List all optional test procedures that are disabled by configuration")
  private val report = OptionDefinition("report", false, "Generate an HTML report containing all the results")
  private val options = OptionDefinition("options", false, "List all of the available configuration file options")
  private val adapters =
    OptionDefinition("adapters", false, "List all of the available network adapters and their addresses")
  private val config = OptionDefinition("config", true, "Path to configuration file")
  private val writeConfig = OptionDefinition("write", true, "Write a default configuration file to the specified path")

  // in the order they're listed in the help
  private val apacheOptions: Options = List(
    adapters,
    config,
    describe,
    disabled,
    help,
    options,
    report,
    run,
    writeConfig).foldLeft(new Options)((sum, o) => sum.addOption(o.id, o.hasArgument, o.description))

}
