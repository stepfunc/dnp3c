// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import java.nio.file.{ Files, Path }

import io.stepfunc.dnp3c.conformance.api.{ Options, RunSummary }
import io.stepfunc.dnp3c.conformance.api.options.TestOptions
import io.stepfunc.dnp3c.conformance.application.{ TestRunner, TextFile }
import io.stepfunc.dnp3c.conformance.application.report.{ Report, Resources }
import io.stepfunc.dnp3c.dnp3.IntegrationPlugin
import io.stepfunc.dnp3c.protocol.logging.Colors

/**
 * Runs the test procedures of one revision of the certification procedure against a device, using a plugin to perform
 * the steps the harness can't do over DNP3.
 *
 * The configuration must give the subset level the device is configured for (`outstation.subset.level`), which the
 * revision must test. Each procedure's log, capture and result are written to its own folder in the folder for the
 * revision and level (`test.output.directory`/revision id/L level, e.g. `results/2.7/L2`), replacing those of an
 * earlier run. Creating a harness does no I/O.
 *
 * A harness can be reused for one run after another, but not for runs that overlap, and a report shouldn't be written
 * while a run on the same output directory is in progress. Harnesses that run at the same time need their own
 * devices, plugins and output directories, and plugins that don't prompt, since prompts read the process's standard
 * input. The harness logs to the console, and never exits the JVM.
 *
 * @param revision the revision whose procedures run, e.g. `Revisions.v2_7`
 */
final class Harness(options: Options, plugin: IntegrationPlugin, revision: Revision) {

  /**
   * Run the procedures of a procedure or section, or "all" of them, in order, stopping at the first that fails.
   *
   * A procedure fails when one of its steps or validations does, including a plugin action or verification that
   * throws.
   *
   * @param id a procedure or section of the revision, e.g. "8.6.5.4" or "8.6", or "all"
   * @throws IllegalArgumentException if the revision has no procedure or section with the id, before anything runs
   * @throws api.ConfigurationException if the configuration has no subset level, or one the revision doesn't test, or
   *                                    lacks what the revision, or a procedure that will run, needs configured,
   *                                    before anything runs
   * @throws HarnessExecutionException if the harness or the plugin fails around a procedure, e.g. when
   *                                   `startProcedure` throws or the capture can't be opened; it holds the
   *                                   results so far. An interruption propagates as it is.
   */
  def run(id: String): RunSummary = {
    val element = revision.elements.getOrElse(
      id,
      throw new IllegalArgumentException(s"Unknown procedure or section in revision ${revision.id}: $id"))
    TestRunner.run(revision, element, options, plugin)
  }

  /**
   * Write the report of every result in the folder for the revision and the configuration's subset level, from this
   * run and earlier ones.
   *
   * @return the report's path
   */
  def writeReport(): Path = Harness.writeReport(options, revision)
}

object Harness {

  /**
   * Write the report of every result in the folder for a revision and the configuration's subset level, without a
   * plugin
   *
   * @return the report's path
   * @throws api.ConfigurationException as [[Revision.subsetLevel]] does
   */
  def writeReport(options: Options, revision: Revision): Path = {

    val level = revision.subsetLevel(options)
    val path = revision.runDirectory(options)
    Files.createDirectories(path)

    val report = Report.create(path, revision, level)

    Colors.text.printLine("creating HTML report...")
    val file = path.resolve("report.html")
    TextFile.write(file.toFile)(writer =>
      report.toHTML(options.get(TestOptions.deviceUnderTest)).foreach(writer.println))

    // the stylesheet is the tool's, rewritten each time, so a report always looks as the version that wrote it draws it
    Resources.extract("report.css", path.resolve("report.css").toFile)

    file
  }

}
