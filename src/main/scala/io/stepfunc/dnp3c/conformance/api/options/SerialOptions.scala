// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{
  BooleanOption, IntegerOption, OptionGroup, OptionSource, OptionTypes, StringOption, TestOption
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

  def options: List[TestOption] = List(enabled, port, baudRate, dataBits, parity, stopBits)

  def settings(source: OptionSource): SerialSettings = SerialSettings(
    source.get(port),
    source.get(baudRate),
    source.get(dataBits),
    source.get(parity),
    source.get(stopBits))

  def apply(source: OptionSource): SerialSettings = settings(source)
}
