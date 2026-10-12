// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{ IntegerOption, OptionGroup, OptionTypes, StringOption, TestOption }

object TestOptions extends OptionGroup {

  val outputDirectory: StringOption =
    OptionTypes.string(this, "output.directory", "./results", "Directory where test results are saved")

  val deviceUnderTest: StringOption = OptionTypes.string(
    this,
    "deviceUnderTest",
    "",
    "Name of the device under test (DUT) shown in the report, e.g. its vendor, model and version")

  val manualActionWindowMs: IntegerOption = OptionTypes.integer(
    this,
    "manual.actionWindowMs",
    10000,
    "Time a manual tester has to act after pressing ENTER, where the harness must see the effect as it happens",
    Some(1000),
    None
  )

  def prefix: String = "test"
  def description: String = "Options for the test harness itself"
  def options: List[TestOption] = List(outputDirectory, deviceUnderTest, manualActionWindowMs)

}
