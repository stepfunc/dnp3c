// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import java.net.InetSocketAddress

import io.stepfunc.dnp3c.conformance.api.{ OptionGroup, OptionSource, OptionTypes, StringOption, TestOption }
import io.stepfunc.dnp3c.protocol.channel.{ ChannelFactory, TcpClientChannelFactory }

object ChannelOptions extends OptionGroup {

  val host: StringOption = OptionTypes.string(this, "device.address", "127.0.0.1", "IP address of the DUT")
  private val port = OptionTypes.integer(this, "device.port", 20000, "IP port of the DUT", Some(1), Some(65535))
  private val captureNIC =
    OptionTypes.string(
      this,
      "capture.adapter",
      "",
      "Address of the network adapter on which to capture pcaps, as -adapters prints it, e.g. /192.168.1.10; empty for no capture"
    )

  def prefix: String = "tcp"
  def description: String = "TCP options"
  def options: List[TestOption] = List(host, port, captureNIC)

  case class Config(host: String, port: Int, captureNIC: Option[String]) {
    def factory(): ChannelFactory = {
      new TcpClientChannelFactory(new InetSocketAddress(host, port))
    }
  }

  def apply(source: OptionSource): Config = {

    val nic: String = source.get(captureNIC)

    Config(
      source.get(host),
      source.get(port),
      if (nic.isEmpty) None else Some(nic))

  }

}
