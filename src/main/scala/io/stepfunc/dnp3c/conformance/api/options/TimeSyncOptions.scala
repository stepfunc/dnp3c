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
  val supportLanTimeSync: BooleanOption = OptionTypes.boolean(
    this,
    "supportLanTimeSync",
    false,
    "Does the DUT support the LAN time sync method (Record Current Time, function code 24), as its Device Profile " +
      "claims? 3.1r1's 8.7.2 tests it"
  )
  // the with-time and relative-time events of binary or double-bit inputs, e.g. "binaryInput" and "Binary Input", of
  // event group 2, and what a DUT that rejects relative time has 3.1r1 skip
  final class TimedEvents private[TimeSyncOptions] (
      input: String,
      name: String,
      eventGroup: Int,
      relativeTimeSkipped: String) {

    val withTime: BooleanOption = OptionTypes.boolean(
      TimeSyncOptions,
      s"${input}ChangeWithTime",
      true,
      s"Does the DUT support $name Change with Time events (g${eventGroup}v2) ?")
    val withRelativeTime: BooleanOption = OptionTypes.boolean(
      TimeSyncOptions,
      s"${input}ChangeWithRelativeTime",
      true,
      s"Does the DUT support $name Change with Relative Time events (g${eventGroup}v3) ?")
    // 3.1r1: a device without events with time may answer a read for them with IIN2.1, which 1815-2012 allows
    val withTimeRejected: BooleanOption = OptionTypes.boolean(
      TimeSyncOptions,
      s"${input}ChangeWithTimeRejected",
      false,
      s"For a DUT without $name Change with Time events: does it answer a read of g${eventGroup}v2 with IIN2.1, as " +
        "IEEE 1815-2012 allows? 3.1r1 then notes it as compliant with 1815-2012 but not 1815-202x, and skips its " +
        "other with-time tests"
    )
    // 3.1r1: likewise for events with relative time
    val withRelativeTimeRejected: BooleanOption = OptionTypes.boolean(
      TimeSyncOptions,
      s"${input}ChangeWithRelativeTimeRejected",
      false,
      s"For a DUT without $name Change with Relative Time events: does it answer a read of g${eventGroup}v3 with " +
        "IIN2.1, as IEEE 1815-2012 allows? 3.1r1 then notes it as compliant with 1815-2012 but not 1815-202x, and " +
        s"skips $relativeTimeSkipped"
    )
  }

  val binaryInput: TimedEvents = new TimedEvents("binaryInput", "Binary Input", 2, "the rest of 8.14 and 8.15")
  val doubleBitInput: TimedEvents =
    new TimedEvents("doubleBitInput", "Double-bit Input", 4, "its other relative-time tests")

  val timeToUnsynchronizedMs: RequiredIntegerOption = OptionTypes.requiredIntegerAtLeast(
    this,
    "timeToUnsynchronizedMs",
    "The manufacturer's time (in milliseconds) after the maximum time from reset to IIN1-4 assertion until the DUT's " +
      "clock is unsynchronized, which 3.1r1's 8.14.2m waits for a DUT that reports events with relative time and " +
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
    supportLanTimeSync,
    binaryInput.withTime,
    binaryInput.withRelativeTime,
    doubleBitInput.withTime,
    doubleBitInput.withRelativeTime,
    binaryInput.withTimeRejected,
    doubleBitInput.withTimeRejected,
    binaryInput.withRelativeTimeRejected,
    doubleBitInput.withRelativeTimeRejected,
    timeToUnsynchronizedMs,
    maxTimeFromResetToIIN14Ms,
    maxTimeBetweenSyncRequestsMs,
    timeWriteLatencyMs
  )
}
