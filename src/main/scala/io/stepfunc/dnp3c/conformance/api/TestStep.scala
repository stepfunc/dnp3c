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

  def combine(steps: TestStep*): TestStep = new TestStep {
    def description: List[Comment] = steps.flatMap(_.description).toList
    def run(driver: TestDriver): List[DeferredCheck] = steps.flatMap(_.run(driver)).toList
  }

}

case class LabeledStep(label: String, step: TestStep) {
  def appendLabel(s: String): LabeledStep = LabeledStep(label + s, step)
  def +(rhs: TestStep): LabeledStep = LabeledStep(label, TestStep.combine(step, rhs))
}
