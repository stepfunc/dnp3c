// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{ BooleanOption, OptionGroup, OptionTypes, TestOption }

object BehaviorOptions extends OptionGroup {

  val resetsLinkUponReceivingNack: BooleanOption = OptionTypes.boolean(
    this,
    "resetsLinkOnNack",
    false,
    "Does the device issue link reset if it receives a NACK while waiting for ACK?")

  def prefix: String = "behaviors"
  def description: String = "Behaviors that can vary between devices, but still remain conformant"
  def options: List[TestOption] = List(resetsLinkUponReceivingNack)

}
