// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api

trait Label {

  def value: String

  def -->(step: TestStep): LabeledStep = LabeledStep(value, step)
}

object Label {

  val note: Label = Custom("note:")

  def apply(id: Int): Label = Single(id)
  def apply(startId: Int, stopId: Int): Label = Ranged(startId, stopId)
  def apply(custom: String): Label = Custom(custom)

  private case class Single(step: Int) extends Label {
    def value: String = s"step: $step"
  }

  private case class Ranged(start: Int, stop: Int) extends Label {
    assert(start < stop)
    def value: String = s"steps: $start to $stop"
  }

  private case class Custom(value: String) extends Label
}
