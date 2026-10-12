// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{ BooleanOption, IntegerOption, OptionGroup, OptionTypes, TestOption }

object TimeSyncOptions extends OptionGroup {

  val supportSynchronizingTimeFromMaster: BooleanOption = OptionTypes.boolean(
    this,
    "supportSynchronizingTimeFromMaster",
    true,
    "Does the DUT support setting its time via DNP3 from a master (i.e. reports IIN1-4) ?")
  val binaryInputChangeWithTime: BooleanOption = OptionTypes.boolean(
    this,
    "binaryInputChangeWithTime",
    true,
    "Does the DUT support Binary Input Change with Time events (g2v2) ?")
  val binaryInputChangeWithRelativeTime: BooleanOption = OptionTypes.boolean(
    this,
    "binaryInputChangeWithRelativeTime",
    true,
    "Does the DUT support Binary Input Change with Relative Time events (g2v3) ?")
  val doubleBitInputChangeWithTime: BooleanOption = OptionTypes.boolean(
    this,
    "doubleBitInputChangeWithTime",
    true,
    "Does the DUT support Double-bit Input Change with Time events (g4v2) ?")
  val doubleBitInputChangeWithRelativeTime: BooleanOption = OptionTypes.boolean(
    this,
    "doubleBitInputChangeWithRelativeTime",
    true,
    "Does the DUT support Double-bit Input Change with Relative Time events (g4v3) ?")
  val maxTimeFromResetToIIN14Ms: IntegerOption = OptionTypes.integer(
    this,
    "maxTimeFromResetToIIN14Ms",
    0,
    "Maximum time (in milliseconds) from reset to IIN1-4 assertion",
    Some(0),
    None)
  val maxTimeBetweenSyncRequestsMs: IntegerOption = OptionTypes.integer(
    this,
    "maxTimeBetweenSyncRequestsMs",
    0,
    "Maximum time (in milliseconds) between time synchronization requests (IIN1-4 assertions)",
    Some(0),
    None)
  val timeWriteLatencyMs: IntegerOption =
    OptionTypes.integer(this, "timeWriteLatencyMs", 5, "Latency to use when synchronizing the time", Some(0), None)

  override def prefix: String = "timesync"
  override def description: String = "Time synchronization options"

  override def options: List[TestOption] = List(
    supportSynchronizingTimeFromMaster,
    binaryInputChangeWithTime,
    binaryInputChangeWithRelativeTime,
    doubleBitInputChangeWithTime,
    doubleBitInputChangeWithRelativeTime,
    maxTimeFromResetToIIN14Ms,
    maxTimeBetweenSyncRequestsMs,
    timeWriteLatencyMs
  )
}
