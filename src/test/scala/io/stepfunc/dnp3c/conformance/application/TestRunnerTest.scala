// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application

import java.io.Closeable
import java.net.InetSocketAddress
import java.nio.file.{ Files, Path }

import io.stepfunc.dnp3c.conformance.{ Decision, Main, Revision, Revisions, Skip }
import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.ProcedureOutcome.{ Excluded, Failed, Passed, Skipped }
import io.stepfunc.dnp3c.conformance.api.options.{
  ChannelOptions, NetworkOptions, OutstationOptions, SupportOptions, TestOptions
}
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.Message
import io.stepfunc.dnp3c.protocol.channel.LocalBindException
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
    // the rules the runner gave the last driver it opened
    var policies: Option[Policies] = None
    def openDriver(reporter: ProcedureReporter, options: OptionSource, policies: Policies): TestDriver = {
      fail("driver open")
      this.policies = Some(policies)
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
        override protected def steps(options: OptionSource): Iterable[LabeledStep] = List(LabeledStep(
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
        override protected def steps(options: OptionSource): Iterable[LabeledStep] = stepNames.map { s =>
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

  // a procedure with a step and a cleanup step, which returns a check; the step can throw LocalBindException instead
  private def restoring(name: String, fail: Failures, bindFails: Boolean = false): TestProcedure =
    TestProcedure(
      name,
      name,
      new ProcedureDefinition {
        override protected def steps(options: OptionSource): Iterable[LabeledStep] = List(LabeledStep(
          "step",
          new TestStep {
            def description: List[Comment] = Nil
            def run(driver: TestDriver): List[DeferredCheck] = {
              fail(s"step $name")
              if (bindFails)
                throw new LocalBindException(new InetSocketAddress("192.0.2.1", 20000), new Exception("not here"))
              Nil
            }
          }
        ))
        override protected def cleanup(options: OptionSource): Iterable[LabeledStep] = List(LabeledStep(
          "cleanup",
          new TestStep {
            def description: List[Comment] = Nil
            def run(driver: TestDriver): List[DeferredCheck] = {
              fail(s"cleanup $name")
              List(DeferredCheck("restored", Nil, () => fail(s"check cleanup $name")))
            }
          }
        ))
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

    def saved(id: String): Option[SavedResult] = {
      val file = folder.resolve(id).resolve("results.properties").toFile
      if (file.exists()) Some(SavedResult.read(file)) else None
    }

    def result(id: String): Option[ProcedureOutcome] = saved(id).map(_.outcome)

    def logged(id: String): Boolean = Files.exists(folder.resolve(id).resolve("log.html"))

    // at level 2, excluding procedures of revision "t" by id, each with its reason
    def excluding(exclusions: (String, String)*): Options = Options.from(
      Map(TestOptions.outputDirectory.id -> directory.toString, OutstationOptions.subsetLevel.id -> "2") ++
        exclusions.map((id, reason) => s"${TestOptions.exclude.id}.t/$id" -> reason))

    // runs an element of a revision of its own, "t", whose results go in tFolder
    def in(revision: Revision, element: TestElement, options: Options): RunSummary =
      TestRunner.run(revision, element, options, new Plugin(fail), new Environment(fail))

    val tFolder: Path = directory.resolve("t").resolve("L2")
  }

  // a revision "t" of one section, "section", which tests levels 1 and 2
  private def revisionOf(procedures: TestProcedure*): Revision =
    new Revision("t", "test", "2026-01-01", List(1, 2), List(section(procedures*)))

  // like procedure, but it records when its steps are built
  private def configured(name: String, fail: Failures): TestProcedure =
    TestProcedure(
      name,
      name,
      new ProcedureDefinition {
        override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
          fail(s"configure $name")
          procedure(name, fail).definition.configure(options).steps
        }
      }
    )

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
    run.result("a") should equal(Some(ProcedureOutcome.Passed))
    run.logged("a") should equal(true)
  }

  test("stops at the first failed procedure, and lists the ones it didn't reach") {
    val run = new Run("step b")
    val summary = run(section(procedure("a", run.fail), procedure("b", run.fail), procedure("c", run.fail)))
    summary should
      equal(RunSummary(List(ProcedureResult("a", Passed), ProcedureResult("b", Failed("step b failed"))), List("c")))
    summary.succeeded should equal(false)
    run.events.list should not contain "step c"
    run.result("b") should matchPattern { case Some(ProcedureOutcome.Failed(_)) => }
  }

  test("a step that throws an exception without a message fails its procedure, like any other") {
    val run = new Run("silent step a")
    run(procedure("a", run.fail)) should
      equal(RunSummary(List(ProcedureResult("a", Failed("java.lang.Exception"))), Nil))
    run.result("a") should matchPattern { case Some(ProcedureOutcome.Failed(_)) => }
  }

  test("a procedure's cleanup runs after its steps, with its checks, and after a step that fails") {
    val run = new Run()
    run(restoring("a", run.fail)) should equal(RunSummary(List(ProcedureResult("a", Passed)), Nil))
    run.events.list should equal(List(
      "capture open",
      "driver open",
      "start",
      "step a",
      "cleanup a",
      "check cleanup a",
      "end",
      "driver close",
      "capture close"))

    val failing = new Run("step a")
    failing(restoring("a", failing.fail)) should
      equal(RunSummary(List(ProcedureResult("a", Failed("step a failed"))), Nil))
    failing.events.list should contain inOrder ("step a", "cleanup a", "check cleanup a")
  }

  test("a cleanup that fails fails a procedure that otherwise passed") {
    val run = new Run("check cleanup a")
    run(restoring("a", run.fail)).results.map(_.outcome) should
      equal(List(Failed("restoring the DUT failed: check cleanup a failed")))
  }

  test("a local address the harness can't use aborts the run, once the DUT is restored") {
    val run = new Run()
    val ex = the[HarnessExecutionException] thrownBy run(restoring("a", run.fail, bindFails = true))
    ex.procedureId should equal("a")
    run.events.list should contain allOf ("cleanup a", "check cleanup a", "driver close")
  }

  test("an interrupted procedure restores the DUT before the interruption propagates") {
    val run = new Run("interrupt step a")
    an[InterruptedException] should be thrownBy run(restoring("a", run.fail))
    run.events.list should contain inOrder ("step a", "cleanup a", "check cleanup a", "driver close")
  }

  test("skips a procedure that's disabled by an option") {
    val run = new Run()
    val summary = run(procedure("a", run.fail, List(SupportOptions.selfAddress)))
    summary.results.map(_.outcome) should matchPattern { case List(Skipped(_)) => }
    summary.succeeded should equal(true)
    run.events.list should not contain "step a"
    run.result("a") should matchPattern { case Some(ProcedureOutcome.Skipped(_)) => }
  }

  test("a skipped procedure opens nothing and calls no plugin method, but still has a log and a result") {
    val run = new Run()
    run(procedure("a", run.fail, List(SupportOptions.selfAddress)))
    run.events.list should equal(Nil)
    run.logged("a") should equal(true)
    run.result("a") should matchPattern { case Some(ProcedureOutcome.Skipped(_)) => }
  }

  test("a skip replaces an earlier result, so a pass can't outlive a skip that fails to be recorded") {
    val run = new Run()
    val folder = run.folder.resolve("a")
    Files.createDirectories(folder)
    SavedResult.write(
      SavedResult(ProcedureOutcome.Passed, Some("2.7"), Some(2)),
      folder.resolve("results.properties"))
    // the log can't be written: log.html is a folder, which can't be deleted as it isn't empty
    Files.createDirectories(folder.resolve("log.html").resolve("x"))
    an[HarnessExecutionException] should be thrownBy run(procedure("a", run.fail, List(SupportOptions.selfAddress)))
    run.result("a") should equal(None)
  }

  test("an excluded procedure isn't configured or run, opens nothing and calls no plugin method, and the run goes on") {
    val run = new Run()
    val revision = revisionOf(configured("a", run.fail), configured("b", run.fail), configured("c", run.fail))
    val summary = run.in(revision, revision.all, run.excluding("b" -> "Assign Class isn't supported yet"))
    summary should equal(RunSummary(
      List(
        ProcedureResult("a", Passed),
        ProcedureResult("b", Excluded("Assign Class isn't supported yet")),
        ProcedureResult("c", Passed)),
      Nil))
    // the run succeeded, but the device isn't shown to conform to b
    summary.succeeded should equal(true)
    summary.exclusions.map(_.id) should equal(List("b"))
    // nothing at all for b
    def ran(name: String) = List(
      "capture open",
      "driver open",
      "start",
      s"configure $name",
      s"step $name",
      "end",
      "driver close",
      "capture close")
    run.events.list should equal(ran("a") ++ ran("c"))

    val saved = SavedResult.read(run.tFolder.resolve("b").resolve("results.properties").toFile)
    saved should equal(SavedResult(Excluded("Assign Class isn't supported yet"), Some("t"), Some(2)))
    Files.readString(run.tFolder.resolve("b").resolve("log.html")) should include(
      "NOT RUN — EXCLUDED b: Assign Class isn't supported yet")
  }

  test("a failure after an exclusion still stops the run") {
    val run = new Run("step b")
    val revision = revisionOf(procedure("a", run.fail), procedure("b", run.fail), procedure("c", run.fail))
    val summary = run.in(revision, revision.all, run.excluding("a" -> "gap"))
    summary should equal(
      RunSummary(List(ProcedureResult("a", Excluded("gap")), ProcedureResult("b", Failed("step b failed"))), List("c")))
    summary.succeeded should equal(false)
    summary.exclusions.map(_.id) should equal(List("a"))
  }

  test("an exclusion replaces the procedure's earlier result, log and capture") {
    for (earlier <- List(Passed, Failed("step a failed"))) {
      val run = new Run()
      val revision = revisionOf(procedure("a", run.fail))
      val folder = run.tFolder.resolve("a")
      Files.createDirectories(folder)
      SavedResult.write(SavedResult(earlier, Some("t"), Some(2), List("a note")), folder.resolve("results.properties"))
      Files.writeString(folder.resolve("log.html"), "the earlier log")
      Files.writeString(folder.resolve("dump.pcap"), "the earlier capture")
      run.in(revision, revision.all, run.excluding("a" -> "gap"))
      SavedResult.read(folder.resolve("results.properties").toFile) should equal(
        SavedResult(Excluded("gap"), Some("t"), Some(2)))
      Files.readString(folder.resolve("log.html")) should not include "the earlier log"
      Files.exists(folder.resolve("dump.pcap")) should equal(false)
    }
  }

  test("an exclusion that fails to be recorded aborts the run, and leaves no earlier pass") {
    val run = new Run()
    val revision = revisionOf(procedure("a", run.fail), procedure("b", run.fail))
    val folder = run.tFolder.resolve("b")
    Files.createDirectories(folder)
    SavedResult.write(SavedResult(Passed, Some("t"), Some(2)), folder.resolve("results.properties"))
    // the log can't be written: log.html is a folder, which can't be deleted as it isn't empty
    Files.createDirectories(folder.resolve("log.html").resolve("x"))
    val ex = the[HarnessExecutionException] thrownBy run.in(revision, revision.all, run.excluding("b" -> "gap"))
    ex.procedureId should equal("b")
    ex.partial should equal(RunSummary(List(ProcedureResult("a", Passed), ProcedureResult("b", Excluded("gap"))), Nil))
    Files.exists(folder.resolve("results.properties")) should equal(false)
  }

  test("an exclusion must name a procedure of the revision, whatever the run selects") {
    val run = new Run()
    val revision = revisionOf(procedure("a", run.fail), procedure("b", run.fail))
    for (id <- List("9.9", "section", "all")) {
      val ex = the[ConfigurationException] thrownBy run.in(revision, revision.elements("a"), run.excluding(id -> "gap"))
      ex.problems.map(_.key) should equal(List(s"${TestOptions.exclude.id}.t/$id"))
      (the[ConfigurationException] thrownBy revision.checkExclusions(run.excluding(id -> "gap"))).problems should
        equal(ex.problems)
    }
    run.events.list should equal(Nil)
    // one outside the selection is fine
    run.in(revision, revision.elements("a"), run.excluding("b" -> "gap")).results should equal(
      List(ProcedureResult("a", Passed)))
  }

  test("a procedure that's skipped stays skipped when it's excluded too, and only those that run need configuring") {
    val run = new Run()
    def procedure(name: String, levels: Option[Set[Int]] = None) =
      TestProcedure(
        name,
        name,
        new ProcedureDefinition {
          override protected def steps(options: OptionSource): Iterable[LabeledStep] = Nil
          override def configurationProblems(options: OptionSource): List[ConfigProblem] =
            List(ConfigProblem(name, "", "needed"))
        },
        levels
      )
    val revision = revisionOf(procedure("runs"), procedure("excluded"), procedure("level", Some(Set(1))))
    val options = run.excluding("excluded" -> "gap", "level" -> "not at level 2 anyway")
    def decision(id: String) = revision.decide(revision.elements(id).asInstanceOf[TestProcedure], options, 2)
    decision("runs") should equal(Decision.Run)
    decision("excluded") should equal(Decision.Exclude("gap"))
    decision("level") should equal(Decision.Skip(Skip.NotApplicable(List(1))))
    (the[ConfigurationException] thrownBy revision.validate(options)).problems.map(_.key) should equal(List("runs"))
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

  test("the runner, the configuration check and -disabled agree on which procedures run") {
    val run = new Run()
    // at level 2, only "runs" runs: "level" applies at level 1 only, "option" needs an option that's off, and "both"
    // does both; each has a configuration problem when asked to
    def revision(problems: Boolean): Revision = {
      def procedure(name: String, levels: Option[Set[Int]] = None, required: List[OptionCondition] = Nil) =
        TestProcedure(
          name,
          name,
          new ProcedureDefinition {
            override protected def requiredOptions: List[OptionCondition] = required
            override protected def steps(options: OptionSource): Iterable[LabeledStep] = Nil
            override def configurationProblems(options: OptionSource): List[ConfigProblem] =
              if (problems) List(ConfigProblem(name, "", "needed")) else Nil
          },
          levels
        )
      val selfAddress = List[OptionCondition](SupportOptions.selfAddress)
      new Revision(
        "t",
        "test",
        "2026-01-01",
        List(1, 2),
        List(section(
          procedure("runs"),
          procedure("level", Some(Set(1))),
          procedure("option", required = selfAddress),
          procedure("both", Some(Set(1)), selfAddress)))
      )
    }
    val options = run.optionsAt(2)

    val runner = revision(problems = false)
    val results = TestRunner.run(runner, runner.all, options, new Plugin(run.fail), new Environment(run.fail)).results
    val skipped = results.filter(_.outcome.isInstanceOf[Skipped]).map(_.id)
    skipped should equal(List("level", "option", "both"))

    Main.disabledLines(options, runner, 2).filter(_.startsWith("Procedure: ")).map(_.stripPrefix("Procedure: ")) should
      contain theSameElementsAs skipped

    val ex = the[ConfigurationException] thrownBy revision(problems = true).validate(options)
    ex.problems.map(_.key) should equal(results.map(_.id).diff(skipped))
  }

  test("the driver gets the revision's message rules") {
    val run = new Run()
    val rule = new MessagePolicy {
      def id = "rule"
      def description = "a rule"
      def violation(message: Message): Option[String] = None
    }
    val revision = new Revision("t", "test", "2026-01-01", List(2), List(section(procedure("a", run.fail))), List(rule))
    val environment = new Environment(run.fail)
    TestRunner.run(revision, revision.all, run.optionsAt(2), new Plugin(run.fail), environment)
    environment.policies.map(_.all) should equal(Some(List(rule)))
  }

  // a procedure that notes and warns, then fails if told to
  private def remarking(name: String, fail: Failures): TestProcedure =
    TestProcedure(
      name,
      name,
      new ProcedureDefinition {
        override protected def steps(options: OptionSource): Iterable[LabeledStep] = List(LabeledStep(
          "remark",
          new TestStep {
            def description: List[Comment] = Nil
            def run(driver: TestDriver): List[DeferredCheck] = {
              driver.reporter.note("an allowed alternative")
              driver.reporter.warning("compliant with <2012> only")
              fail(s"step $name")
              Nil
            }
          }
        ))
      }
    )

  test("a pass keeps the notes and warnings its procedure made, and logs a warning as one") {
    val run = new Run()
    val summary = run(section(remarking("a", run.fail), procedure("b", run.fail)))
    summary.results.map(r => (r.id, r.notes, r.warnings)) should equal(List(
      ("a", List("an allowed alternative"), List("compliant with <2012> only")),
      ("b", Nil, Nil)))
    summary.passedWithWarnings.map(_.id) should equal(List("a"))
    run.saved("a").map(s => (s.notes, s.warnings)) should equal(
      Some((List("an allowed alternative"), List("compliant with <2012> only"))))
    val log = Files.readString(run.folder.resolve("a").resolve("log.html"))
    log should include regex
      """<tr class="warn">\s*<td>[^<]*</td>\s*<td>[^<]*</td>\s*<td>\s*Warning on the result: compliant with &lt;2012&gt; only"""
  }

  test("a failure keeps no notes or warnings, and isn't a pass with warnings") {
    val run = new Run("step a")
    val summary = run(remarking("a", run.fail))
    summary.results.map(r => (r.notes, r.warnings)) should equal(List((Nil, Nil)))
    summary.passedWithWarnings should equal(Nil)
    run.saved("a").map(s => (s.notes, s.warnings)) should equal(Some((Nil, Nil)))
  }

  test("a saved result records its revision, and why the procedure was skipped") {
    val run = new Run()
    run(procedure("a", run.fail, List(SupportOptions.selfAddress)))
    val saved = run.saved("a").get
    saved.revision should equal(Some("2.7"))
    saved.level should equal(Some(2))
    saved.outcome should matchPattern {
      case ProcedureOutcome.Skipped(reason) if reason.contains(SupportOptions.selfAddress.id) =>
    }
  }

  test("a plugin that fails to start aborts the run, and the procedure gets no result") {
    val run = new Run("start#2")
    Files.createDirectories(run.folder.resolve("b"))
    SavedResult.write(
      SavedResult(ProcedureOutcome.Passed, Some("2.7"), Some(2)),
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
    run.result("a") should equal(Some(ProcedureOutcome.Passed))
  }

  test("a failing deferred check fails the procedure, skips the later checks, and still cleans up") {
    val run = new Run("check 1")
    run(deferring("a", run.fail, "1", "2")).results should equal(List(ProcedureResult("a", Failed("check 1 failed"))))
    run.events.list should not contain "check 2"
    run.events.list.takeRight(3) should equal(List("end", "driver close", "capture close"))
    run.result("a") should matchPattern { case Some(ProcedureOutcome.Failed(_)) => }
  }

  test("a failing step means the deferred checks are never run") {
    val run = new Run("step 2")
    run(deferring("a", run.fail, "1", "2")).results should equal(List(ProcedureResult("a", Failed("step 2 failed"))))
    run.events.list should not contain "check 1"
  }

  test("the capture has the DUT's traffic, and the broadcasts to the multicast group where one is set") {
    def filter(host: String, group: String): String = ProcedureEnvironment.captureFilter(
      Options.from(Map(ChannelOptions.host.id -> host, NetworkOptions.multicastGroup.id -> group)))
    filter("192.168.1.20", "") should equal("host 192.168.1.20")
    filter("192.168.1.20", "239.0.0.1") should equal("host 192.168.1.20 or host 239.0.0.1")
    filter("fd00::20", "ff15::1") should equal("host fd00::20 or host ff15::1")
    // a group 3.1r1's UDP Multicast test wouldn't run with
    filter("192.168.1.20", "192.168.1.30") should equal("host 192.168.1.20")
    filter("192.168.1.20", "group") should equal("host 192.168.1.20")
  }
}
