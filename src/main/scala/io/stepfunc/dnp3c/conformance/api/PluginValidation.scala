// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api

import io.stepfunc.dnp3c.dnp3.app.Message
import io.stepfunc.dnp3c.dnp3.{
  CheckReporter, DeferredCheck, CheckTiming, Comment, IntegrationPlugin, MessageValidation, ProcedureReporter,
  TestDriver
}

/**
 * A validation that the integration plugin performs on a received message
 */
final class PluginValidation private (
    val description: List[Comment],
    timing: CheckTiming,
    check: (IntegrationPlugin, CheckReporter, Message) => IterableOnce[DeferredCheck]) extends MessageValidation {

  override def validate(reporter: ProcedureReporter, message: Message): List[DeferredCheck] =
    check(reporter.plugin, reporter.checkReporter(timing), message).iterator.toList

  // let the plugin check after the procedure's steps, where checking now (e.g. asking a manual tester) could hold up
  // a timed step; apply it before combining validations
  def deferrable: PluginValidation = new PluginValidation(description, CheckTiming.Deferrable, check)
}

object PluginValidation {
  def apply(description: Comment*)(check: (IntegrationPlugin, CheckReporter, Message) => IterableOnce[DeferredCheck])
      : PluginValidation = new PluginValidation(description.toList, CheckTiming.Immediate, check)
}

/**
 * A step in which the integration plugin checks the device, e.g. that an output operated
 */
final class PluginCheck private (
    val description: List[Comment],
    timing: CheckTiming,
    check: (IntegrationPlugin, CheckReporter) => IterableOnce[DeferredCheck]) extends TestStep {

  override def run(driver: TestDriver): List[DeferredCheck] =
    check(driver.reporter.plugin, driver.reporter.checkReporter(timing)).iterator.toList

  // let the plugin check after the procedure's steps, where checking now (e.g. asking a manual tester) could hold up
  // a timed step
  def deferrable: PluginCheck = new PluginCheck(description, CheckTiming.Deferrable, check)
}

object PluginCheck {
  def apply(description: Comment*)(check: (IntegrationPlugin, CheckReporter) => IterableOnce[DeferredCheck])
      : PluginCheck =
    new PluginCheck(description.toList, CheckTiming.Immediate, check)
}
