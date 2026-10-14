// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import io.stepfunc.dnp3c.conformance.api.{ OptionSource, Options, ProcedureDefinition, ProcedureOutcome, TestProcedure }
import io.stepfunc.dnp3c.conformance.application.TestRunner
import io.stepfunc.dnp3c.dnp3.TestDriver

import scala.util.Try

// runs a definition against a test DUT as a run does: its steps until one fails, then their deferred checks if none did,
// then its cleanup
object Run {

  def outcome(definition: ProcedureDefinition, driver: TestDriver, options: OptionSource): ProcedureOutcome =
    TestRunner.runSteps(TestProcedure("test", "test", definition), options, driver)

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
    (config.steps ++ config.cleanup).count(s => Try(s.step.run(driver)).isFailure)
  }

  def everyStep(definition: ProcedureDefinition, driver: TestDriver, settings: Map[String, String] = Map()): Int =
    everyStep(definition, driver, Options.from(settings))
}
