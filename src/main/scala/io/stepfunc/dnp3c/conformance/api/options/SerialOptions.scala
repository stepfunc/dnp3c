// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{
  BooleanOption, IndexListOption, IntegerOption, OptionGroup, OptionSource, OptionTypes, StringOption, TestOption
}
import io.stepfunc.dnp3c.protocol.channel.SerialSettings

/** A serial connection to the DUT, in place of TCP */
object SerialOptions extends OptionGroup {

  def prefix: String = "serial"
  def description: String = "Serial options"

  val enabled: BooleanOption = OptionTypes.boolean(
    this,
    "enabled",
    false,
    "Connect to the DUT over a serial port (serial.port) rather than over TCP (tcp.device.*)")

  val port: StringOption =
    OptionTypes.string(this, "port", "", "The serial port the DUT is on, e.g. /dev/ttyUSB0 or COM3")

  val baudRate: IntegerOption =
    OptionTypes.integer(this, "baudRate", 9600, "The serial port's speed, in bits per second", Some(50), None)

  val dataBits: IntegerOption =
    OptionTypes.integer(this, "dataBits", 8, "The number of data bits in each character", Some(5), Some(8))

  val parity: StringOption =
    OptionTypes.choice(this, "parity", "none", "The parity bit", List("none", "even", "odd", "mark", "space"))

  val stopBits: IntegerOption =
    OptionTypes.integer(this, "stopBits", 1, "The number of stop bits", Some(1), Some(2))

  val multiDrop: BooleanOption = OptionTypes.boolean(
    this,
    "multiDrop",
    false,
    "Does the DUT support a multi-drop physical layer, e.g. RS-485? 3.1r1's 8.10.2 tests it over serial, with the " +
      "devices at serial.otherDevices on the same drop"
  )

  val otherDevices: IndexListOption = OptionTypes.indexList(
    this,
    "otherDevices",
    "The DNP3 addresses of the other devices on the DUT's drop, at least two, for 3.1r1's 8.10.2",
    Nil)

  val rtsDelays: StringOption = OptionTypes.string(
    this,
    "rtsDelays",
    "",
    "The DUT's settings of the delays between raising carrier (RTS) and transmitting, and between transmitting and " +
      "dropping it, that 3.1r1's 8.10.2 step 9c tests, as before/after pairs in ms, e.g. 0/0, 10/5, 50/20; empty " +
      "where they aren't configurable"
  )

  val collisionAvoidance: BooleanOption = OptionTypes.boolean(
    this,
    "collisionAvoidance",
    false,
    "Does the DUT's Device Profile claim collision avoidance? 3.1r1's 8.12.2 tests it over serial, timing its " +
      "responses against noise on the line"
  )

  val collisionDetection: StringOption = OptionTypes.choice(
    this,
    "collisionDetection",
    "data",
    "How the DUT tells the line is in use: by the data on it, or by carrier (DCD), which the harness drives with RTS " +
      "while it sends noise",
    List("data", "carrier")
  )

  val fixedDelayMs: IntegerOption = OptionTypes.integer(
    this,
    "fixedDelayMs",
    0,
    "The DUT's collision avoidance fixed delay, in ms, as it's set for 3.1r1's 8.12.2",
    Some(0),
    None)

  val maxRandomDelayMs: IntegerOption = OptionTypes.integer(
    this,
    "maxRandomDelayMs",
    0,
    "The DUT's collision avoidance maximum random delay, in ms, as it's set for 3.1r1's 8.12.2",
    Some(0),
    None)

  val otherFixedDelayMs: IntegerOption = OptionTypes.integer(
    this,
    "otherFixedDelayMs",
    0,
    "A different fixed delay, in ms, that 3.1r1's 8.12.2 sets the DUT to at step 10, with serial.otherMaxRandomDelayMs",
    Some(0),
    None
  )

  val otherMaxRandomDelayMs: IntegerOption = OptionTypes.integer(
    this,
    "otherMaxRandomDelayMs",
    0,
    "A different maximum random delay, in ms, that 3.1r1's 8.12.2 sets the DUT to at step 10",
    Some(0),
    None)

  val timingToleranceMs: IntegerOption = OptionTypes.integer(
    this,
    "timingToleranceMs",
    20,
    "How far a time measured on the serial line may be off, in ms: what the adapter adds, e.g. a USB adapter's " +
      "latency timer, both ways. 3.1r1's 8.12.2 allows it on each delay it measures",
    Some(0),
    None
  )

  def options: List[TestOption] = List(
    enabled,
    port,
    baudRate,
    dataBits,
    parity,
    stopBits,
    multiDrop,
    otherDevices,
    rtsDelays,
    collisionAvoidance,
    collisionDetection,
    fixedDelayMs,
    maxRandomDelayMs,
    otherFixedDelayMs,
    otherMaxRandomDelayMs,
    timingToleranceMs
  )

  def settings(source: OptionSource): SerialSettings = SerialSettings(
    source.get(port),
    source.get(baudRate),
    source.get(dataBits),
    source.get(parity),
    source.get(stopBits))

  def apply(source: OptionSource): SerialSettings = settings(source)
}
