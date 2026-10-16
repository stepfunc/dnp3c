// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{
  ExclusionsOption, IntegerOption, OptionGroup, OptionTypes, StringOption, TestOption
}

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

  val timingToleranceMs: IntegerOption = OptionTypes.integer(
    this,
    "timingToleranceMs",
    500,
    "How far a time the harness measures may be off, in ms, both ways: how late a message may arrive past a timeout " +
      "the harness waits on, and how far an interval it times may be from the one expected, e.g. an unsolicited " +
      "retry from the confirm timeout. A larger value is more forgiving of a slow network or a busy machine, both " +
      "early and late. For the serial line's own timing, see serial.timingToleranceMs",
    Some(0),
    None
  )

  val exclude: ExclusionsOption = OptionTypes.exclusions(
    this,
    "exclude",
    "Procedures to leave out of a run, one property each, with the reason: " +
      "test.exclude.<revision id>/<procedure id> = <reason>. A run leaves out only its own revision's, so one " +
      "configuration can exclude procedures of several revisions. " +
      "An excluded procedure applies to the DUT but isn't run, and is reported as not run, so the DUT isn't shown to " +
      "conform to it. A procedure for a feature the DUT lacks is skipped by its supports.* option instead",
    "test.exclude.3.1r1/8.26.2 = Assign Class isn't supported yet"
  )

  def prefix: String = "test"
  def description: String = "Options for the test harness itself"
  def options: List[TestOption] =
    List(outputDirectory, deviceUnderTest, manualActionWindowMs, timingToleranceMs, exclude)

}
