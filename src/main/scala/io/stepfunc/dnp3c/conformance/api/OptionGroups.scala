// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api

import io.stepfunc.dnp3c.conformance.api.options._

/**
 * Registry of all option groups
 */
object OptionGroups {

  val list: List[OptionGroup] = List(
    TestOptions,
    ChannelOptions,
    SerialOptions,
    LinkOptions,
    OutstationOptions,
    BinaryOutputOptions,
    AnalogOutputOptions,
    ClassOptions,
    EventBufferOptions,
    BehaviorOptions,
    TimeSyncOptions,
    FlagTestOptions,
    BinaryInputOptions,
    DoubleBitInputOptions,
    CounterOptions,
    AnalogInputOptions,
    UnsolicitedOptions,
    AttributeOptions,
    NetworkOptions,
    SupportOptions
  )
}
