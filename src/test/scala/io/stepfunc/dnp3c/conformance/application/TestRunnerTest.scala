// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application

import java.io.Closeable
import java.nio.file.{ Files, Path }

import io.stepfunc.dnp3c.conformance.{ Revision, Revisions }
import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.ProcedureOutcome.{ Failed, Passed, Skipped }
import io.stepfunc.dnp3c.conformance.api.options.{ OutstationOptions, SupportOptions, TestOptions }
import io.stepfunc.dnp3c.dnp3._
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class TestRunnerTest extends AnyFunSuite with Matchers {

  // what happened during a run, in order
  private class Events {
    val list = mutable.ListBuffer.empty[String]
    def apply(event: String): Unit = list += event
  }

  // throws at the events it's told to fail at, either every time ("start") or only the nth time ("start#2"),
  // is interrupted at the ones it's told to ("interrupt start"), and throws without a message at others ("silent start")
  private class Failures(events: Events, failAt: Set[String]) {
    def apply(event: String): Unit = {
      events(event)
      val occurrence = events.list.count(_ == event)
      if (failAt.contains(event) || failAt.contains(s"$event#$occurrence")) throw new Exception(s"$event failed")
      if (failAt.contains(s"interrupt $event")) throw new InterruptedException(s"$event interrupted")
      if (failAt.contains(s"silent $event")) throw new Exception()
    }
  }

  // keeps the reporter it's given, as the real driver does
  private class Driver(fail: Failures, reporter: ProcedureReporter) extends StubDriver(reporter) {
    override def close(): Unit = fail("driver close")
  }

  private class Environment(fail: Failures) extends ProcedureEnvironment {
    def openCapture(options: OptionSource, path: Path): Option[Closeable] = {
      fail("capture open")
      Some(() => fail("capture close"))
    }
    def openDriver(reporter: ProcedureReporter, options: OptionSource): TestDriver = {
      fail("driver open")
      new Driver(fail, reporter)
    }
  }

  private class Plugin(fail: Failures) extends DefaultIntegrationPlugin {
    override def startProcedure(reporter: PluginReporter): Unit = fail("start")
    override def endProcedure(reporter: PluginReporter): Unit = fail("end")
  }

  private def procedure(name: String, fail: Failures, required: List[OptionCondition] = Nil): TestProcedure =
    TestProcedure(
      name,
      name,
      new ProcedureDefinition {
        override protected def requiredOptions: List[OptionCondition] = required
        protected def steps(options: OptionSource): Iterable[LabeledStep] = List(LabeledStep(
          "step",
          new TestStep {
            def description: List[Comment] = Nil
            def run(driver: TestDriver): List[DeferredCheck] = {
              fail(s"step $name")
              Nil
            }
          }))
      }
    )

  // a procedure whose steps each record themselves and return a deferred check that records itself
  private def deferring(name: String, fail: Failures, stepNames: String*): TestProcedure =
    TestProcedure(
      name,
      name,
      new ProcedureDefinition {
        protected def steps(options: OptionSource): Iterable[LabeledStep] = stepNames.map { s =>
          LabeledStep(
            "step",
            new TestStep {
              def description: List[Comment] = Nil
              def run(driver: TestDriver): List[DeferredCheck] = {
                fail(s"step $s")
                List(DeferredCheck(s, Nil, () => fail(s"check $s")))
              }
            }
          )
        }
      }
    )

  private def section(elements: TestProcedure*): TestSection = section(None, elements*)

  private def section(levels: Option[Set[Int]], elements: TestProcedure*): TestSection = {
    val sectionLevels = levels
    new TestSection {
      def id: String = "section"
      def description: String = "section"
      override def levels: Option[Set[Int]] = sectionLevels
      override def procedures: Iterable[TestProcedure] = elements
    }
  }

  private class Run(failAt: String*) {
    val events = new Events
    val fail = new Failures(events, failAt.toSet)
    val directory: Path = Files.createTempDirectory("dnp3c")
    def optionsAt(level: Int): Options = Options.from(Map(
      TestOptions.outputDirectory.id -> directory.toString,
      OutstationOptions.subsetLevel.id -> level.toString))
    private val options = optionsAt(2)

    // the revision's folder, where the procedures' folders go
    val folder: Path = directory.resolve(Revisions.v2_7.id).resolve("L2")

    def apply(element: TestElement): RunSummary =
      TestRunner.run(Revisions.v2_7, element, options, new Plugin(fail), new Environment(fail))

    def saved(id: String): Option[TestResult.Saved] = {
      val file = folder.resolve(id).resolve("results.properties").toFile
      if (file.exists()) Some(TestResult.read(file)) else None
    }

    def result(id: String): Option[TestResult] = saved(id).map(_.result)

    def logged(id: String): Boolean = Files.exists(folder.resolve(id).resolve("log.html"))
  }

  test("runs a procedure between the plugin's callbacks, and cleans up in reverse") {
    val run = new Run()
    run(procedure("a", run.fail)) should equal(RunSummary(List(ProcedureResult("a", Passed)), Nil))
    run.events.list should equal(List(
      "capture open",
      "driver open",
      "start",
      "step a",
      "end",
      "driver close",
      "capture close"))
    run.result("a") should equal(Some(TestResult.Pass))
    run.logged("a") should equal(true)
  }

  test("stops at the first failed procedure, and lists the ones it didn't reach") {
    val run = new Run("step b")
    val summary = run(section(procedure("a", run.fail), procedure("b", run.fail), procedure("c", run.fail)))
    summary should
      equal(RunSummary(List(ProcedureResult("a", Passed), ProcedureResult("b", Failed("step b failed"))), List("c")))
    summary.succeeded should equal(false)
    run.events.list should not contain "step c"
    run.result("b") should equal(Some(TestResult.Fail))
  }

  test("a step that throws an exception without a message fails its procedure, like any other") {
    val run = new Run("silent step a")
    run(procedure("a", run.fail)) should
      equal(RunSummary(List(ProcedureResult("a", Failed("java.lang.Exception"))), Nil))
    run.result("a") should equal(Some(TestResult.Fail))
  }

  test("skips a procedure that's disabled by an option") {
    val run = new Run()
    val summary = run(procedure("a", run.fail, List(SupportOptions.selfAddress)))
    summary.results.map(_.outcome) should matchPattern { case List(Skipped(_)) => }
    summary.succeeded should equal(true)
    run.events.list should not contain "step a"
    run.result("a") should equal(Some(TestResult.Skip))
  }

  test("a skipped procedure opens nothing and calls no plugin method, but still has a log and a result") {
    val run = new Run()
    run(procedure("a", run.fail, List(SupportOptions.selfAddress)))
    run.events.list should equal(Nil)
    run.logged("a") should equal(true)
    run.result("a") should equal(Some(TestResult.Skip))
  }

  test("a skip replaces an earlier result, so a pass can't outlive a skip that fails to be recorded") {
    val run = new Run()
    val folder = run.folder.resolve("a")
    Files.createDirectories(folder)
    TestResult.write(
      TestResult.Saved(TestResult.Pass, Some("2.7"), Some(2), None),
      folder.resolve("results.properties"))
    // the log can't be written: log.html is a folder
    Files.createDirectories(folder.resolve("log.html"))
    an[HarnessExecutionException] should be thrownBy run(procedure("a", run.fail, List(SupportOptions.selfAddress)))
    run.result("a") should equal(None)
  }

  test("a procedure that doesn't apply at the device's subset level is skipped, saying so") {
    val run = new Run()
    val revision =
      new Revision(
        "t",
        "test",
        "2026-01-01",
        List(1, 2),
        List(section(levels = Some(Set(1)), procedure("a", run.fail))))
    val summary =
      TestRunner.run(revision, revision.all, run.optionsAt(2), new Plugin(run.fail), new Environment(run.fail))
    summary.results.map(_.outcome) should matchPattern {
      case List(Skipped(reason)) if reason.contains("level(s) 1") =>
    }
    run.events.list should equal(Nil)
  }

  test("a saved result records its revision, and why the procedure was skipped") {
    val run = new Run()
    run(procedure("a", run.fail, List(SupportOptions.selfAddress)))
    val saved = run.saved("a").get
    saved.revision should equal(Some("2.7"))
    saved.level should equal(Some(2))
    saved.reason.get should include(SupportOptions.selfAddress.id)
  }

  test("a plugin that fails to start aborts the run, and the procedure gets no result") {
    val run = new Run("start#2")
    Files.createDirectories(run.folder.resolve("b"))
    TestResult.write(
      TestResult.Saved(TestResult.Pass, Some("2.7"), Some(2), None),
      run.folder.resolve("b").resolve("results.properties"))

    val ex = intercept[HarnessExecutionException](run(section(
      procedure("a", run.fail),
      procedure("b", run.fail),
      procedure("c", run.fail))))

    ex.procedureId should equal("b")
    ex.partial should equal(RunSummary(List(ProcedureResult("a", Passed)), List("c")))
    ex.getCause.getMessage should equal("start failed")
    // the earlier pass is gone, and only what was opened is closed
    run.result("b") should equal(None)
    run.logged("b") should equal(true)
    run.events.list.drop(7) should equal(List("capture open", "driver open", "start", "driver close", "capture close"))
  }

  test("a cleanup failure aborts the run, keeping the procedure's outcome") {
    val run = new Run("step a", "driver close")
    val ex = intercept[HarnessExecutionException](run(procedure("a", run.fail)))
    ex.partial should equal(RunSummary(List(ProcedureResult("a", Failed("step a failed"))), Nil))
    ex.getCause.getMessage should equal("driver close failed")
    run.events.list.takeRight(3) should equal(List("end", "driver close", "capture close"))
    run.result("a") should equal(None)
  }

  test("every cleanup step runs, and later failures are suppressed in the first") {
    val run = new Run("end", "capture close")
    val ex = intercept[HarnessExecutionException](run(procedure("a", run.fail)))
    ex.getCause.getMessage should equal("end failed")
    ex.getCause.getSuppressed.map(_.getMessage).toList should equal(List("capture close failed"))
    run.events.list.takeRight(3) should equal(List("end", "driver close", "capture close"))
  }

  test("an interruption propagates after cleaning up") {
    val run = new Run("interrupt step a")
    intercept[InterruptedException](run(procedure("a", run.fail)))
    run.events.list.takeRight(3) should equal(List("end", "driver close", "capture close"))
    run.result("a") should equal(None)
  }

  test("a driver that fails to open still closes the capture") {
    val run = new Run("driver open")
    val ex = intercept[HarnessExecutionException](run(procedure("a", run.fail)))
    ex.partial should equal(RunSummary(Nil, Nil))
    run.events.list should equal(List("capture open", "driver open", "capture close"))
  }

  test("deferred checks run after all the steps, in step order, before the plugin ends the procedure") {
    val run = new Run()
    run(deferring("a", run.fail, "1", "2")) should equal(RunSummary(List(ProcedureResult("a", Passed)), Nil))
    run.events.list.drop(3).take(5) should equal(List("step 1", "step 2", "check 1", "check 2", "end"))
    run.result("a") should equal(Some(TestResult.Pass))
  }

  test("a failing deferred check fails the procedure, skips the later checks, and still cleans up") {
    val run = new Run("check 1")
    run(deferring("a", run.fail, "1", "2")).results should equal(List(ProcedureResult("a", Failed("check 1 failed"))))
    run.events.list should not contain "check 2"
    run.events.list.takeRight(3) should equal(List("end", "driver close", "capture close"))
    run.result("a") should equal(Some(TestResult.Fail))
  }

  test("a failing step means the deferred checks are never run") {
    val run = new Run("step 2")
    run(deferring("a", run.fail, "1", "2")).results should equal(List(ProcedureResult("a", Failed("step 2 failed"))))
    run.events.list should not contain "check 1"
  }
}
