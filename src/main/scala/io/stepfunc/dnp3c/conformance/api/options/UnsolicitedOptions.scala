// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{ BooleanOption, IntegerOption, OptionGroup, OptionTypes, TestOption }

object UnsolicitedOptions extends OptionGroup {

  val unsolicitedConfirmTimeoutMs: IntegerOption = OptionTypes.integer(
    this,
    "unsolicitedConfirmTimeoutMs",
    5000,
    "Unsolicited confirmation timeout value (in ms)",
    Some(1),
    None)
  val otherMasterAddress: IntegerOption = OptionTypes.integer(
    this,
    "otherMasterAddress",
    2,
    "Other Master address where unsolicited responses can be configured to send to",
    Some(0),
    Some(0xFFEF))
  val hasMinNumEventsBeforeUnsolicited: BooleanOption = OptionTypes.boolean(
    this,
    "hasMinNumEventsBeforeUnsolicited",
    false,
    "Does the device supports a minimum amount of event data before sending an unsolicited response?")

  val holdOffMs: IntegerOption = OptionTypes.integer(
    this,
    "holdOffMs",
    0,
    "Maximum time (in ms) the DUT may wait after an event before sending a data-filled unsolicited response",
    Some(0),
    None)

  override def prefix: String = "unsolicited"
  override def description: String = "Unsolicited response options"

  override def options: List[TestOption] = List(
    unsolicitedConfirmTimeoutMs,
    otherMasterAddress,
    hasMinNumEventsBeforeUnsolicited,
    holdOffMs)
}
