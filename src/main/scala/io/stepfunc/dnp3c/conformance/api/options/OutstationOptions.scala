// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{ IntegerOption, OptionGroup, OptionTypes, TestOption }
import io.stepfunc.dnp3c.dnp3.transport.Transport

object OutstationOptions extends OptionGroup {

  val selectTimeout: IntegerOption =
    OptionTypes.integer(this, "select.timeout.ms", 2000, "Device select timeout in milliseconds", Some(1), None)
  val responseTimeout: IntegerOption =
    OptionTypes.integer(this, "response.timeout.ms", 2000, "Response timeout in milliseconds", Some(1), None)
  val appConfirmTimeout: IntegerOption = OptionTypes.integer(
    this,
    "app.confirm.timeout.ms",
    2000,
    "DUT's application layer confirm timeout for solicited responses in milliseconds",
    Some(1),
    None)
  val configuredFragmentSize: IntegerOption =
    OptionTypes.integer(
      this,
      "fragmentSize",
      2048,
      "Configurable fragment size (preferably not 2048)",
      // what fits in one link frame
      Some(Transport.maxPayload),
      None
    )

  def prefix: String = "outstation"

  def description: String = "Outstation application layer options"

  def options: List[TestOption] = List(
    selectTimeout,
    responseTimeout,
    appConfirmTimeout,
    configuredFragmentSize)
}
