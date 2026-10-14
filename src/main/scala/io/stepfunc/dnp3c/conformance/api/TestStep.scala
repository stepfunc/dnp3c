// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api

import io.stepfunc.dnp3c.dnp3.{ Comment, TestDriver }
import io.stepfunc.dnp3c.dnp3.DeferredCheck

trait TestStep {

  def description: List[Comment]

  def run(driver: TestDriver): List[DeferredCheck]

  def +(rhs: TestStep): TestStep = TestStep.combine(this, rhs)
}

object TestStep {

  /** a step that does what act does, and defers no checks */
  def apply(description: => List[Comment])(act: TestDriver => Unit): TestStep = {
    def describe = description
    new TestStep {
      def description: List[Comment] = describe
      def run(driver: TestDriver): List[DeferredCheck] = {
        act(driver)
        Nil
      }
    }
  }

  def combine(steps: TestStep*): TestStep = new TestStep {
    def description: List[Comment] = steps.flatMap(_.description).toList
    def run(driver: TestDriver): List[DeferredCheck] = steps.flatMap(_.run(driver)).toList
  }

}

case class LabeledStep(label: String, step: TestStep) {
  def appendLabel(s: String): LabeledStep = LabeledStep(label + s, step)
  def +(rhs: TestStep): LabeledStep = LabeledStep(label, TestStep.combine(step, rhs))
}

/**
 * A change to the DUT, and the step that undoes it, for one run of a procedure: the change arms the undo before it runs,
 * so that a change that fails part way is undone too, and the undo runs only once it's armed, and once. A procedure
 * puts restore in its cleanup, and also among its steps where it publishes the undo as one.
 */
final class Undoable(change: TestStep, undo: TestStep) {
  private var armed = false

  val apply: TestStep = new TestStep {
    def description: List[Comment] = change.description
    def run(driver: TestDriver): List[DeferredCheck] = {
      armed = true
      change.run(driver)
    }
  }

  val restore: TestStep = new TestStep {
    def description: List[Comment] = undo.description
    def run(driver: TestDriver): List[DeferredCheck] =
      if (!armed) Nil
      else {
        armed = false
        undo.run(driver)
      }
  }
}
