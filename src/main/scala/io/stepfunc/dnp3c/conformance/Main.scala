// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import java.io.{ File, IOException }
import java.nio.file.Path

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.application.pcap.PacketCapture
import io.stepfunc.dnp3c.conformance.application.{ Console, Logo, OptionFileWriter }
import io.stepfunc.dnp3c.dnp3.{ DefaultIntegrationPlugin, IntegrationPlugin }
import io.stepfunc.dnp3c.protocol.logging.Colors
import org.apache.commons.cli.ParseException
import org.pcap4j.core.PcapNetworkInterface

import scala.util.control.NonFatal

object Main {

  def main(args: Array[String]): Unit = {
    run(args, new DefaultIntegrationPlugin())
  }

  def run(args: Array[String], integrationPlugin: IntegrationPlugin): Unit = {
    Logo.print()
    System.exit(execute(args, integrationPlugin))
  }

  def execute(args: Array[String], integrationPlugin: IntegrationPlugin): Int = {
    val config =
      try Console.readConfiguration(args)
      catch {
        case ex: ParseException =>
          Colors.error.printLine(ErrorText.of(ex))
          Colors.text.printLine("")
          printHelp(None)
          return -1
      }
    execute(config, integrationPlugin)
  }

  def execute(config: Console.Configuration, integrationPlugin: IntegrationPlugin): Int = {

    // the commands that don't need a configuration file

    if (config.help) {
      config.revision match {
        case None => printHelp(None)
        case Some(id) =>
          Revisions.find(id) match {
            case Some(revision) => printHelp(Some(revision))
            case None =>
              Colors.error.printLine(unknownRevision(id))
              return -1
          }
      }
      return 0
    }

    if (config.listRevisions) {
      printRevisions()
      return 0
    }

    if (config.options) {
      printOptions()
      return 0
    }

    if (config.adapters) {
      try printNetworkAdapters()
      catch {
        case NonFatal(ex) =>
          Colors.error.printLine(s"Couldn't list the network adapters: $ex")
          return -1
      }
      return 0
    }

    config.writeConfigFile match {
      case Some(path) =>
        try OptionFileWriter.write(path, OptionGroups.list)
        catch {
          case ex: IOException =>
            Colors.error.printLine(s"Couldn't write the configuration file: $ex")
            return -1
        }
        Colors.text.printLine("wrote default configuration file to: " + path)
        return 0
      case None =>
    }

    if (!config.listDisabled && config.describe.isEmpty && !config.report && config.run.isEmpty) {
      Colors.text.printLine("You must specify a command")
      Colors.text.printLine("")
      printHelp(None)
      return -2
    }

    // every command from here on uses a revision's procedures
    val revision: Revision = config.revision match {
      case None =>
        Colors.error.printLine(
          s"Choose the revision of the test procedures with -rev, e.g. -rev ${Revisions.list.last.id} (see -revs)")
        return -1
      case Some(id) =>
        Revisions.find(id) match {
          case Some(r) => r
          case None =>
            Colors.error.printLine(unknownRevision(id))
            return -1
        }
    }

    val options: Options =
      try {
        loadOptions(config.config)
      } catch {
        case ex: ConfigurationException =>
          Colors.error.printLine(ErrorText.of(ex))
          return -1
        case NonFatal(ex) =>
          Colors.error.printLine(s"Couldn't read or create the configuration file: $ex")
          return -1
      }

    // and the subset level the device is configured for, which the revision must test
    val level =
      try revision.subsetLevel(options)
      catch {
        case ex: ConfigurationException =>
          Colors.error.printLine(ErrorText.of(ex))
          return -1
      }

    if (config.listDisabled) {
      listDisabled(options, revision, level)
      return 0
    }

    config.describe match {
      case Some(id) =>
        revision.elements.get(id) match {
          case Some(section: TestSection) => print(section, Int.MaxValue, 0)
          case Some(procedure: TestProcedure) if !revision.levelsOf(procedure).contains(level) =>
            println(s"Not applicable at subset level $level: ${notApplicable(revision, procedure)}")
          case Some(procedure: TestProcedure) =>
            // building the steps checks the options the procedure uses, e.g. 8.23's device attributes
            try printDescription(procedure, options)
            catch {
              case NonFatal(ex) =>
                Colors.error.printLine(s"Can't describe ${procedure.id}: ${ErrorText.of(ex)}")
                return -1
            }
          case None =>
            Colors.error.printLine(s"Unknown id in revision ${revision.id}: $id")
            return -1
        }
        return 0
      case None =>
    }

    if (config.report) {
      return if (writeReport(Harness.writeReport(options, revision))) 0 else -1
    }

    config.run match {
      case Some(id) =>
        if (!revision.elements.contains(id)) {
          Colors.error.printLine(s"Unknown id in revision ${revision.id}: $id")
          return -1
        }

        val harness = new Harness(options, integrationPlugin, revision)

        var ran = false
        var reported = false
        try {
          ran =
            try {
              val summary = harness.run(id)
              if (!summary.succeeded) Colors.error.printLine("Abort due to test failure")
              summary.succeeded
            } catch {
              case NonFatal(ex) =>
                Colors.error.printLine(ErrorText.of(ex))
                false
            }
        } finally {
          // the report covers every result so far, however the run ended
          reported = writeReport(harness.writeReport())
        }

        if (ran && reported) 0 else -1
      case None => 0
    }
  }

  private def writeReport(write: => Path): Boolean =
    try {
      Colors.text.printLine("Wrote the report: " + write.toAbsolutePath.normalize)
      true
    } catch {
      case NonFatal(ex) =>
        Colors.error.printLine("Failed to write the report: " + ErrorText.of(ex))
        false
    }

  // the file given with -config, or ./device.properties, which is created with the defaults if it doesn't exist
  private def loadOptions(config: Option[File]): Options = config match {
    case Some(file) => Options.load(file.toPath)
    case None =>
      val file = new File("./device.properties")

      if (file.exists()) {
        Colors.text.printLine("Using default device properties file: " + file.toString)
        Colors.text.printLine("")
      } else {
        Colors.text.printLine("Creating default device properties file: " + file.toString)
        Colors.text.printLine("")
        OptionFileWriter.write(file, OptionGroups.list)
      }

      Options.load(file.toPath)
  }

  // with a revision, its top-level sections; without one, how to find them
  def printHelp(revision: Option[Revision]): Unit = {
    Console.printHelp()
    Colors.text.printLine("")
    revision match {
      case Some(r) =>
        Colors.text.printLine(s"sections of revision ${r.id}: ")
        Colors.text.printLine("")
        r.sections.foreach(p => print(p, 0, 0))
      case None =>
        Colors.text.printLine(
          "-revs lists the revisions of the test procedures; -rev <id> -help lists a revision's sections")
    }
    Colors.text.printLine("")
  }

  def printRevisions(): Unit = {
    Colors.text.printLine("revisions: ")
    Colors.text.printLine("")
    Revisions.list.foreach { r =>
      val levels = r.subsetLevels.mkString(", ")
      Colors.text.printLine(s"${r.id.padTo(10, ' ')}${r.date}  ${r.title} (subset levels: $levels)")
    }
    Colors.text.printLine("")
  }

  private def unknownRevision(id: String): String =
    s"Unknown revision: $id. The revisions are: ${Revisions.list.map(_.id).mkString(", ")}"

  private def notApplicable(revision: Revision, p: TestProcedure): String =
    s"${p.id} applies to subset level(s) ${revision.levelsOf(p).toList.sorted.mkString(", ")}"

  def listDisabled(options: OptionSource, revision: Revision, level: Int): Unit = {
    Colors.text.printLine("Listing optional test procedures disabled by configuration: ")
    Colors.text.printLine("")

    revision.flatMapProcedures(p => Option.when(!revision.levelsOf(p).contains(level))(p)).foreach { p =>
      Colors.text.printLine("Procedure: " + p.id)
      Colors.text.printLine(s"    Not applicable at subset level $level")
    }

    val pairs: Iterable[(TestProcedure, BooleanOption)] =
      revision.flatMapProcedures(p => p.disabledBy(options).map(x => (p, x.option)))

    pairs.foreach { pair =>
      Colors.text.printLine("Procedure: " + pair._1.id)
      Colors.text.printLine("    Option: " + pair._2.id + " == " + options.get(pair._2))
    }
  }

  def printNetworkAdapters(): Unit = {
    def print(interface: PcapNetworkInterface): Unit = {
      println()
      println(interface.getName)
      println()
      interface.getAddresses.forEach { a =>
        println(" " * 4 + a.getAddress.toString)
      }
    }

    PacketCapture.interfaces.foreach(print)
  }

  def printOptions(): Unit = {

    def printOption(option: TestOption): Unit = {
      Colors.success.printLine("// " + option.description)
      if (option.required) Colors.text.printLine(option.id + " = (required)")
      else Colors.text.printLine(option.id + " = " + option.defaultAsString)
    }

    def printGroup(group: OptionGroup): Unit = {
      Colors.text.printLine("# " + group.description)
      Colors.text.printLine("")
      group.options.foreach(printOption)
      Colors.text.printLine("")
    }

    OptionGroups.list.foreach(printGroup)
  }

  def printDescription(p: TestProcedure, options: OptionSource): Unit = {
    if (p.incomplete) {
      println("Not implemented")
      return
    }

    // inject an option wrapper so we can track what options the test uses

    val tracker = new OptionTracker(options)
    val steps = p.configure(tracker).steps
    val usedOptions = tracker.getUsedOptions.toList.sortBy(x => x.id)

    if (usedOptions.nonEmpty) {
      println("Procedure uses the following options:")
      usedOptions.foreach { opt =>
        println("   - " + opt.id + " : " + opt.description)
      }
    }

    steps.foreach { named =>
      println("%s".format(named.label))
      named.step.description.foreach(println)
    }
  }

  def print(element: TestElement, depth: Int, numSpaces: Int): Unit = {

    val spaces = " " * numSpaces
    def line = (spaces + element.id).padTo(30, " ").mkString + element.description

    val colors: Colors = element match {
      case x: TestProcedure if x.incomplete => Colors.incomplete
      case _ => Colors.text
    }

    colors.printLine(line)

    if (depth > 0) {
      element.subElements.foreach(e => print(e, depth - 1, numSpaces + 2))
    }

  }

}
