// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{ IntegerOption, OptionGroup, OptionTypes, TestOption }

object LinkOptions extends OptionGroup {

  val source: IntegerOption =
    OptionTypes.integer(this, "source", 1, "Link layer address of the test harness (master)", Some(0), Some(0xFFEF))
  val destination: IntegerOption =
    OptionTypes.integer(this, "destination", 1024, "Link layer address of the DUT (outstation)", Some(0), Some(0xFFEF))
  val numRetry: IntegerOption =
    OptionTypes.integer(this, "num.retry", 3, "Number of configured link layer retries", Some(1), Some(10))
  val timeout: IntegerOption =
    OptionTypes.integer(this, "timeout.ms", 1000, "Link layer timeout in milliseconds", Some(100), None)

  def prefix: String = "link"
  def description: String = "link-layer options"
  def options: List[TestOption] = List(
    source,
    destination,
    numRetry,
    timeout)
}
