// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import io.stepfunc.dnp3c.conformance.api.{ OptionSource, Options, ProcedureDefinition, ProcedureOutcome, TestProcedure }
import io.stepfunc.dnp3c.conformance.application.TestRunner
import io.stepfunc.dnp3c.dnp3.TestDriver

import java.io.StringReader

import scala.util.Try

// runs a definition against a test DUT as a run does: its steps until one fails, then their deferred checks if none did,
// then its cleanup; with the input on stdin, which answers the plugin's prompts, so a test never waits on the terminal
class Run(input: String) {

  // runs f with the input on stdin
  def apply[A](f: => A): A = Console.withIn(new StringReader(input))(f)

  def outcome(definition: ProcedureDefinition, driver: TestDriver, options: OptionSource): ProcedureOutcome = {
    // a run skips a procedure the configuration disables, without running it
    definition.disabledBy(options).foreach(unmet => throw new IllegalArgumentException(s"disabled: ${unmet.message}"))
    apply(TestRunner.runSteps(TestProcedure("test", "test", definition), options, driver))
  }

  def outcome(
      definition: ProcedureDefinition,
      driver: TestDriver,
      settings: Map[String, String] = Map()): ProcedureOutcome =
    outcome(definition, driver, Options.from(settings))

  def passes(definition: ProcedureDefinition, driver: TestDriver, options: OptionSource): Boolean =
    outcome(definition, driver, options) == ProcedureOutcome.Passed

  def passes(definition: ProcedureDefinition, driver: TestDriver, settings: Map[String, String] = Map()): Boolean =
    passes(definition, driver, Options.from(settings))

  // runs it, and throws unless it passes, with why
  def passing(definition: ProcedureDefinition, driver: TestDriver, options: OptionSource): Unit =
    outcome(definition, driver, options) match {
      case ProcedureOutcome.Passed => ()
      case other => throw new Exception(s"The procedure didn't pass: $other")
    }

  def passing(definition: ProcedureDefinition, driver: TestDriver, settings: Map[String, String] = Map()): Unit =
    passing(definition, driver, Options.from(settings))

  /**
   * Runs every step whatever happens, then the cleanup's, without their deferred checks, for tests that look at all
   * that a procedure sends, or count the steps that fail, rather than at its outcome; the number of steps that failed
   */
  def everyStep(definition: ProcedureDefinition, driver: TestDriver, options: OptionSource): Int = {
    val config = definition.configure(options)
    apply((config.steps ++ config.cleanup).count(s => Try(s.step.run(driver)).isFailure))
  }

  def everyStep(definition: ProcedureDefinition, driver: TestDriver, settings: Map[String, String] = Map()): Int =
    everyStep(definition, driver, Options.from(settings))
}

// with nothing on stdin: a prompt fails its step, as it does when stdin isn't a terminal
object Run extends Run("") {

  // with the given lines on stdin, e.g. "\np\n" to press ENTER, then answer pass
  def answering(input: String): Run = new Run(input)
}
