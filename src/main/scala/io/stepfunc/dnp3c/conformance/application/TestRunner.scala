// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application

import java.io.Closeable
import java.nio.file.{ Files, Path }

import io.stepfunc.dnp3c.conformance.Revision
import io.stepfunc.dnp3c.conformance.application.pcap.PacketCapture
import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{
  ChannelOptions, LinkOptions, OutstationOptions, SerialOptions, TestOptions, UnsolicitedOptions
}
import io.stepfunc.dnp3c.conformance.application.report.ReportLogger
import io.stepfunc.dnp3c.dnp3.{ DefaultTestDriver, IntegrationPlugin, Policies, ProcedureReporter, TestDriver }
import io.stepfunc.dnp3c.protocol.channel.{ BenchException, SerialChannelFactory }
import io.stepfunc.dnp3c.protocol.logging.Logger
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE

import scala.collection.mutable
import scala.util.control.NonFatal
import io.stepfunc.dnp3c.dnp3.DeferredCheck

/**
 * What a procedure needs from outside the harness, which tests replace
 */
private[application] trait ProcedureEnvironment {
  def openCapture(options: OptionSource, path: Path): Option[Closeable]
  def openDriver(reporter: ProcedureReporter, options: OptionSource, policies: Policies): TestDriver
}

private[application] object ProcedureEnvironment {

  val default: ProcedureEnvironment = new ProcedureEnvironment {

    // there's nothing to capture on a serial line
    def openCapture(options: OptionSource, path: Path): Option[Closeable] = {
      val channel = ChannelOptions(options)
      if (options.get(SerialOptions.enabled)) None
      else channel.captureNIC.map(nic => PacketCapture.create(nic, channel.host).open(path.toString))
    }

    def openDriver(reporter: ProcedureReporter, options: OptionSource, policies: Policies): TestDriver =
      new DefaultTestDriver(
        reporter,
        if (options.get(SerialOptions.enabled)) new SerialChannelFactory(SerialOptions(options))
        else ChannelOptions(options).factory(),
        UInt16LE(options.get(LinkOptions.source)),
        UInt16LE(options.get(LinkOptions.destination)),
        options.get(LinkOptions.timeout),
        options.get(OutstationOptions.responseTimeout),
        options.get(UnsolicitedOptions.unsolicitedConfirmTimeoutMs),
        policies = policies
      )
  }
}

object TestRunner {

  /**
   * Run the procedures in an element, in order, stopping at the first that fails
   *
   * @throws HarnessExecutionException if the harness or the plugin fails while running a procedure
   */
  private[conformance] def run(
      revision: Revision,
      element: TestElement,
      options: OptionSource,
      integrationPlugin: IntegrationPlugin): RunSummary =
    run(revision, element, options, integrationPlugin, ProcedureEnvironment.default)

  private[application] def run(
      revision: Revision,
      element: TestElement,
      options: OptionSource,
      integrationPlugin: IntegrationPlugin,
      environment: ProcedureEnvironment): RunSummary = {

    // the sections and procedures in the order they run: a section's subsections, then its procedures
    def walk(e: TestElement): List[TestElement] = e match {
      case s: TestSection => s :: (s.sections.toList ++ s.procedures.toList).flatMap(walk)
      case p: TestProcedure => List(p)
    }

    val elements = walk(element)
    val procedures = elements.collect { case p: TestProcedure => p }

    // the configuration must be one the revision can test, for the procedures selected, before anything runs
    val level = revision.validate(options, procedures)
    val results = mutable.ListBuffer[ProcedureResult]()

    def failed: Boolean = results.exists(_.outcome.isInstanceOf[ProcedureOutcome.Failed])
    def unreachedAfter(count: Int): List[String] = procedures.drop(count).map(_.id)
    def aborted(p: TestProcedure, outcome: Option[ProcedureOutcome], error: Throwable): HarnessExecutionException = {
      val partial = results.toList ++ outcome.map(ProcedureResult(p.id, _))
      new HarnessExecutionException(p.id, RunSummary(partial, unreachedAfter(results.size + 1)), error)
    }

    elements.iterator.takeWhile(_ => !failed).foreach {
      case s: TestSection =>
        ConsoleLogger.info("Entering test section: " + s.id + " - " + s.description)
      case p: TestProcedure =>
        try {
          results += runProcedure(revision, level, p, options, integrationPlugin, environment)
        } catch {
          case ex: ProcedureAborted => throw aborted(p, ex.outcome, ex.error)
          case NonFatal(ex) => throw aborted(p, None, ex)
        }
    }

    RunSummary(results.toList, unreachedAfter(results.size))
  }

  // the harness or the plugin failed, after the procedure reached the outcome, if any
  private class ProcedureAborted(val outcome: Option[ProcedureOutcome], val error: Throwable) extends Exception(error)

  /**
   * Why a procedure won't run, if it won't: it isn't implemented, it doesn't apply at the subset level, or the device
   * lacks a feature it needs. Decided before anything is opened, so a skipped procedure never touches the device or the
   * plugin.
   */
  private def skipReason(
      revision: Revision,
      level: Int,
      procedure: TestProcedure,
      options: OptionSource): Option[String] =
    if (procedure.incomplete) Some("not implemented")
    else {
      val levels = revision.levelsOf(procedure)
      if (!levels.contains(level))
        Some(
          s"not applicable: it applies to subset level(s) ${levels.toList.sorted.mkString(", ")}, and the device is " +
            s"configured for level $level")
      else
        procedure.disabledBy(options).map(unmet => s"disabled because ${unmet.message}")
    }

  private def runProcedure(
      revision: Revision,
      level: Int,
      procedure: TestProcedure,
      options: OptionSource,
      integrationPlugin: IntegrationPlugin,
      environment: ProcedureEnvironment): ProcedureResult = {

    val path = revision.runDirectory(options).resolve(procedure.id)
    val resultsFile = path.resolve(ResultFiles.result)
    val title = s"${procedure.id} ${procedure.description} (revision ${revision.id}, subset level $level)"

    // a pass keeps the notes the procedure made about it
    def save(result: ProcedureOutcome, notes: List[String] = Nil): Unit = {
      val kept = if (result == ProcedureOutcome.Passed) notes else Nil
      SavedResult.write(SavedResult(result, Some(revision.id), Some(level), kept), resultsFile)
    }

    skipReason(revision, level, procedure, options) match {
      case Some(reason) =>
        return ProcedureResult(procedure.id, skip(procedure, path, title, reason, options, r => save(r)))
      case None =>
    }

    // run once the procedure ends, however it ends, the last added first
    var cleanup: List[() => Unit] = Nil
    var reporter: Option[ProcedureReporter] = None
    var outcome: Option[ProcedureOutcome] = None

    val failure: Option[Throwable] =
      try {
        Files.createDirectories(path)
        // results from an earlier run must not look like this run's
        Files.deleteIfExists(resultsFile)
        Files.deleteIfExists(path.resolve(ResultFiles.capture))

        val htmlLogger = new ReportLogger(
          path.resolve(ResultFiles.log).toFile,
          title,
          options.get(TestOptions.deviceUnderTest))
        cleanup ::= (() => htmlLogger.write())

        environment.openCapture(options, path.resolve(ResultFiles.capture)).foreach(c => cleanup ::= (() => c.close()))

        val testReporter = new ProcedureReporter(Logger.aggregate(ConsoleLogger, htmlLogger), integrationPlugin)
        reporter = Some(testReporter)
        testReporter.info("Running procedure: " + procedure.id)

        // the revision's rules for the messages the procedure receives
        val driver = environment.openDriver(testReporter, options, new Policies(revision.policies))
        cleanup ::= (() => driver.shutdown())

        integrationPlugin.startProcedure(testReporter.pluginReporter())
        cleanup ::= (() => integrationPlugin.endProcedure(testReporter.pluginReporter()))

        outcome = Some(runSteps(procedure, options, driver))
        None
      } catch {
        case NonFatal(ex) =>
          reporter.foreach(_.error(s"The procedure couldn't be run: ${ErrorText.of(ex)}"))
          Some(ex)
        case ex: Throwable =>
          // an interruption, or a fatal error: clean up, then let it propagate
          throw cleanUp(cleanup, Some(ex)).getOrElse(ex)
      }

    cleanUp(cleanup, failure).foreach {
      case NonFatal(ex) => throw new ProcedureAborted(outcome, ex)
      case ex => throw ex
    }

    // the procedure, and everything around it, completed, so the outcome is known
    val result = outcome.get
    val notes = if (result == ProcedureOutcome.Passed) reporter.toList.flatMap(_.notes) else Nil
    try save(result, notes)
    catch { case NonFatal(ex) => throw new ProcedureAborted(outcome, ex) }
    ProcedureResult(procedure.id, result, notes)
  }

  // records a procedure that won't run: its log says why, and nothing is opened
  private def skip(
      procedure: TestProcedure,
      path: Path,
      title: String,
      reason: String,
      options: OptionSource,
      save: ProcedureOutcome => Unit): ProcedureOutcome = {
    val outcome = ProcedureOutcome.Skipped(reason)
    try {
      Files.createDirectories(path)
      // results from an earlier run must not look like this run's, even if writing this one fails
      Files.deleteIfExists(path.resolve(ResultFiles.result))
      Files.deleteIfExists(path.resolve(ResultFiles.capture))
      val htmlLogger =
        new ReportLogger(path.resolve(ResultFiles.log).toFile, title, options.get(TestOptions.deviceUnderTest))
      try Logger.aggregate(ConsoleLogger, htmlLogger).info(s"Skipped procedure: ${procedure.id}, $reason")
      finally htmlLogger.write()
      save(outcome)
    } catch { case NonFatal(ex) => throw new ProcedureAborted(Some(outcome), ex) }
    outcome
  }

  // runs every cleanup action; the first failure is returned, with the later ones suppressed in it
  private def cleanUp(actions: List[() => Unit], failure: Option[Throwable]): Option[Throwable] =
    actions.foldLeft(failure) { (first, action) =>
      try {
        action()
        first
      } catch {
        case ex: Throwable =>
          first match {
            case Some(f) =>
              if (f ne ex) f.addSuppressed(ex)
              first
            case None => Some(ex)
          }
      }
    }

  // a deferred check and where it came from: e.g. "8.11.2.5.3 steps: 4 to 5 (step 27 of the procedure)"
  private case class Pending(origin: String, check: DeferredCheck)

  // e.g. "8.11.2.5.3": a label that names a subsection, rather than a step
  private val subsection = """\d+(\.\d+)+""".r

  // the procedure's steps, then their deferred checks, then its cleanup, for a procedure that's implemented, applies at
  // the run's level and is enabled; tests run procedures through it too
  private[conformance] def runSteps(
      procedure: TestProcedure,
      options: OptionSource,
      driver: TestDriver): ProcedureOutcome = {

    // checks returned by completed steps, to run once every step has
    val pending = mutable.ListBuffer[Pending]()
    var started = 0
    var cleanup: Iterable[LabeledStep] = Nil

    val outcome = try {

      val config = procedure.configure(options)
      cleanup = config.cleanup

      var heading: Option[String] = None
      val steps = config.steps.iterator.zipWithIndex
      var ended = false
      while (!ended && steps.hasNext) {
        val (x, ordinal) = steps.next()
        if (subsection.matches(x.label)) heading = Some(x.label)
        driver.reporter.note(x.label, x.step.description.map(_.toString))
        try {
          val checks = x.step.run(driver)
          val origin = (heading.toList :+ x.label).mkString(" ") + s" (step ${ordinal + 1} of the procedure)"
          pending ++= checks.map(Pending(origin, _))
        } catch {
          case end: EndProcedure =>
            driver.reporter.info(s"The procedure ends early, its remaining steps skipped: ${end.reason}")
            ended = true
        }
      }

      for ((p, n) <- pending.zipWithIndex) {
        driver.reporter.info(s"Deferred check ${n + 1} of ${pending.size}, from ${p.origin}: ${p.check.description}")
        started = n + 1
        p.check.check()
      }

      driver.reporter.info("Passed procedure: " + procedure.id)
      ProcedureOutcome.Passed

    } catch {
      case ex: BenchException =>
        // the test bench can't do what the procedure needs, which says nothing of the DUT
        cleanUpDevice(cleanup, driver)
        throw ex
      case NonFatal(ex) =>
        driver.reporter.error(ErrorText.of(ex))
        // the checks after the one that failed, or all of them if a step failed
        val unevaluated = pending.size - started
        if (unevaluated > 0) driver.reporter.warn(s"$unevaluated deferred check(s) weren't evaluated")

        driver.reporter.error("Failed procedure: " + procedure.id)
        ProcedureOutcome.Failed(ErrorText.of(ex))
      case ex: Throwable =>
        // an interruption, or a fatal error: restore the DUT if it can be, then let it propagate
        try cleanUpDevice(cleanup, driver)
        catch { case other: Throwable => if (other ne ex) ex.addSuppressed(other) }
        throw ex
    }

    // a DUT that can't be restored fails a procedure it otherwise passed
    cleanUpDevice(cleanup, driver) match {
      case Some(ex) if outcome == ProcedureOutcome.Passed =>
        driver.reporter.error("Failed procedure: " + procedure.id)
        ProcedureOutcome.Failed(s"restoring the DUT failed: ${ErrorText.of(ex)}")
      case _ => outcome
    }
  }

  // runs the procedure's cleanup steps, each with its checks, however the procedure ended; the first failure is returned
  // once all have run
  private def cleanUpDevice(steps: Iterable[LabeledStep], driver: TestDriver): Option[Throwable] =
    steps.foldLeft(Option.empty[Throwable]) { (first, x) =>
      driver.reporter.note(x.label, x.step.description.map(_.toString))
      try {
        x.step.run(driver).foreach(_.check())
        first
      } catch {
        case NonFatal(ex) =>
          driver.reporter.error(s"Cleanup failed: ${ErrorText.of(ex)}")
          first.orElse(Some(ex))
      }
    }

}
