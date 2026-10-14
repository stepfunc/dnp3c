// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{
  BooleanOption, IntegerOption, OptionGroup, OptionTypes, TestOption, RequiredIntegerOption
}

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
  // 3.1r1: a device without events with time may answer a read for them with IIN2.1, which 1815-2012 allows
  val binaryInputChangeWithTimeRejected: BooleanOption = OptionTypes.boolean(
    this,
    "binaryInputChangeWithTimeRejected",
    false,
    "For a DUT without Binary Input Change with Time events: does it answer a read of g2v2 with IIN2.1, as IEEE " +
      "1815-2012 allows? 3.1r1 then notes it as compliant with 1815-2012 but not 1815-202x, and skips its other " +
      "with-time tests"
  )
  val doubleBitInputChangeWithTimeRejected: BooleanOption = OptionTypes.boolean(
    this,
    "doubleBitInputChangeWithTimeRejected",
    false,
    "For a DUT without Double-bit Input Change with Time events: does it answer a read of g4v2 with IIN2.1, as IEEE " +
      "1815-2012 allows? 3.1r1 then notes it as compliant with 1815-2012 but not 1815-202x, and skips its other " +
      "with-time tests"
  )
  // 3.1r1: a device without events with relative time may answer a read for them with IIN2.1
  val binaryInputChangeWithRelativeTimeRejected: BooleanOption = OptionTypes.boolean(
    this,
    "binaryInputChangeWithRelativeTimeRejected",
    false,
    "For a DUT without Binary Input Change with Relative Time events: does it answer a read of g2v3 with IIN2.1, as " +
      "IEEE 1815-2012 allows? 3.1r1 then notes it as compliant with 1815-2012 but not 1815-202x, and skips the rest " +
      "of 8.14 and 8.15"
  )
  val doubleBitInputChangeWithRelativeTimeRejected: BooleanOption = OptionTypes.boolean(
    this,
    "doubleBitInputChangeWithRelativeTimeRejected",
    false,
    "For a DUT without Double-bit Input Change with Relative Time events: does it answer a read of g4v3 with IIN2.1, " +
      "as IEEE 1815-2012 allows? 3.1r1 then notes it as compliant with 1815-2012 but not 1815-202x, and skips its " +
      "other relative-time tests"
  )
  val timeToUnsynchronizedMs: RequiredIntegerOption = OptionTypes.requiredIntegerAtLeast(
    this,
    "timeToUnsynchronizedMs",
    "The manufacturer's time (in milliseconds) after the maximum time from reset to IIN1-4 assertion until the DUT's " +
      "clock is unsynchronized, which 3.1r1's 8.14.2-13 waits for a DUT that reports events with relative time and " +
      "asks for the time",
    0
  )
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
    binaryInputChangeWithTimeRejected,
    doubleBitInputChangeWithTimeRejected,
    binaryInputChangeWithRelativeTimeRejected,
    doubleBitInputChangeWithRelativeTimeRejected,
    timeToUnsynchronizedMs,
    maxTimeFromResetToIIN14Ms,
    maxTimeBetweenSyncRequestsMs,
    timeWriteLatencyMs
  )
}
