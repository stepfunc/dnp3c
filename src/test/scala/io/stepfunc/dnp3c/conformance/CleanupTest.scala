// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.OutstationOptions
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.conformance.common.OperationType
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.Message
import io.stepfunc.dnp3c.dnp3.link.LinkFrame
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

// a procedure's cleanup restores the DUT however it ends, and a procedure may end early
@RunWith(classOf[JUnitRunner])
class CleanupTest extends AnyFunSuite with Matchers {

  private class Plugin extends DefaultIntegrationPlugin {
    val calls = mutable.ListBuffer.empty[String]
    override def cyclePower(reporter: PluginReporter): Unit = calls += "cyclePower"
    override def setLinkLayerConfirm(reporter: PluginReporter, value: Boolean): Unit = calls += s"confirms $value"
    override def uninstallBinaryInputs(reporter: PluginReporter): Unit = calls += "uninstall"
    override def uninstallDoubleBitBinaryInputs(reporter: PluginReporter): Unit = calls += "uninstall"
    override def uninstallBinaryOutputs(reporter: PluginReporter): Unit = calls += "uninstall"
    override def reinstallPoints(reporter: PluginReporter): Unit = calls += "reinstall"
  }

  // a DUT that never answers, so the first step that expects an answer fails
  private class Silent(val plugin: Plugin = new Plugin) extends RespondingDriver {
    override protected def testPlugin: IntegrationPlugin = plugin
    override def writeMessage(message: Message, options: LinkOverride): Unit = ()
    override def writeFrame(frame: LinkFrame): Unit = ()
    override def readFrame(timeout: Option[Int]): Option[LinkFrame] = timedOut(timeout)
  }

  private def record(calls: mutable.ListBuffer[String], what: String): TestStep = new TestStep {
    def description: List[Comment] = List(what)
    def run(driver: TestDriver): List[DeferredCheck] = {
      calls += what
      Nil
    }
  }

  private def failing: TestStep = new TestStep {
    def description: List[Comment] = List("fail")
    def run(driver: TestDriver): List[DeferredCheck] = throw new Exception("failed")
  }

  test("a procedure that ends early skips its remaining steps, then runs the deferred checks so far and the cleanup") {
    val calls = mutable.ListBuffer.empty[String]
    def ending(checkPasses: Boolean) = new ProcedureDefinition {
      override protected def plan(options: OptionSource): Plan = Plan(
        List(
          1 --> new TestStep {
            def description: List[Comment] = List("a step with a deferred check")
            def run(driver: TestDriver): List[DeferredCheck] = {
              calls += "step 1"
              List(DeferredCheck(
                "the check",
                Nil,
                () => {
                  calls += "check"
                  if (!checkPasses) throw new Exception("the check failed")
                }))
            }
          },
          2 --> new TestStep {
            def description: List[Comment] = List("end")
            def run(driver: TestDriver): List[DeferredCheck] = throw new EndProcedure("the DUT may end it here")
          },
          3 --> record(calls, "step 3")
        ),
        List(Label("cleanup") --> record(calls, "cleanup"))
      )
    }
    Run.outcome(ending(checkPasses = true), new Silent) should equal(ProcedureOutcome.Passed)
    calls.toList should equal(List("step 1", "check", "cleanup"))
    calls.clear()
    Run.outcome(ending(checkPasses = false), new Silent) shouldBe a[ProcedureOutcome.Failed]
    calls.toList should equal(List("step 1", "check", "cleanup"))
  }

  test("an Undoable change is undone by the cleanup only once it's been made, and only once") {
    val calls = mutable.ListBuffer.empty[String]
    def definition(failFirst: Boolean, publishedUndo: Boolean) = new ProcedureDefinition {
      override protected def plan(options: OptionSource): Plan = {
        val change = Undoable(record(calls, "change"), record(calls, "undo"))
        Plan(
          (if (failFirst) List(1 --> failing) else Nil) ++ List(2 --> change.apply) ++
            (if (publishedUndo) List(3 --> change.restore) else List(3 --> failing)),
          List(Label("cleanup") --> change.restore)
        )
      }
    }
    Run.outcome(definition(failFirst = true, publishedUndo = false), new Silent) shouldBe a[ProcedureOutcome.Failed]
    calls.toList should equal(Nil)
    Run.outcome(definition(failFirst = false, publishedUndo = false), new Silent) shouldBe a[ProcedureOutcome.Failed]
    calls.toList should equal(List("change", "undo"))
    calls.clear()
    Run.outcome(definition(failFirst = false, publishedUndo = true), new Silent) should equal(ProcedureOutcome.Passed)
    calls.toList should equal(List("change", "undo"))
  }

  test("points uninstalled or disabled come back, and link confirms go off, when a later step fails") {
    val binaryOutputs = OperationType.BinaryOutput.support
    for ((procedure, settings, expected) <- List(
        (v27.Procedure8_13_2_3, Map.empty[String, String], List("uninstall", "reinstall")),
        (
          v31r1.DisableDoubleBit.static,
          Map(Inputs.doubleBit.points.id -> "true", Inputs.doubleBit.disabling.id -> "true"),
          List("uninstall", "reinstall")),
        (
          v27.Procedure8_2_1_2_10,
          Map(binaryOutputs.commands.id -> "true", binaryOutputs.disabling.id -> "true"),
          List("uninstall", "reinstall")),
        (
          v27.Procedure6_6_3_1,
          Map.empty[String, String],
          List("confirms true", "cyclePower", "confirms false", "cyclePower"))
      ))
      withClue(procedure) {
        val dut = new Silent
        Run.outcome(procedure, dut, settings) shouldBe a[ProcedureOutcome.Failed]
        dut.plugin.calls.toList should equal(expected)
      }
  }

  test("a definition narrowed with requiring needs its extra condition too, and keeps its cleanup") {
    val calls = mutable.ListBuffer.empty[String]
    val inner = new ProcedureDefinition {
      override protected def requiredOptions: List[OptionCondition] = List(Inputs.binary.points)
      override protected def plan(options: OptionSource): Plan =
        Plan(List(1 --> failing), List(Label("cleanup") --> record(calls, "cleanup")))
    }
    val narrowed = inner.requiring(OptionCondition(Inputs.binary.reads))
    val on = Map(Inputs.binary.points.id -> "true", Inputs.binary.reads.id -> "true")
    narrowed.disabledBy(Options.from(on + (Inputs.binary.reads.id -> "false"))) should not equal None
    narrowed.disabledBy(Options.from(on + (Inputs.binary.points.id -> "false"))) should not equal None
    val enabledBy = narrowed.configure(Options.from(on)).steps.head.step.description.mkString("\n")
    enabledBy should include(Inputs.binary.reads.id)
    enabledBy should include(Inputs.binary.points.id)
    Run.outcome(narrowed, new Silent, on) shouldBe a[ProcedureOutcome.Failed]
    calls.toList should equal(List("cleanup"))
  }

  test("a run checks the configuration problems of the procedures that will run, each once") {
    val problem = ConfigProblem("some.option", "", "needed")
    def needing(required: Boolean) = new ProcedureDefinition {
      override protected def requiredOptions: List[OptionCondition] =
        if (required) List(Inputs.binary.points) else Nil
      override protected def steps(options: OptionSource): Iterable[LabeledStep] = Nil
      override def configurationProblems(options: OptionSource): List[ConfigProblem] = List(problem)
    }
    val revision = new Revision("t", "t", "2000-01-01", List(1), Nil)
    val level = Map(OutstationOptions.subsetLevel.id -> "1")
    def problems(settings: Map[String, String], definitions: ProcedureDefinition*): List[ConfigProblem] =
      scala.util.Try(revision.validate(
        Options.from(level ++ settings),
        definitions.map(d => TestProcedure("id", "t", d)))) match {
        case scala.util.Failure(e: ConfigurationException) => e.problems
        case _ => Nil
      }
    problems(Map(), needing(required = false), needing(required = false)) should equal(List(problem))
    problems(Map(Inputs.binary.points.id -> "false"), needing(required = true)) should equal(Nil)
  }
}
