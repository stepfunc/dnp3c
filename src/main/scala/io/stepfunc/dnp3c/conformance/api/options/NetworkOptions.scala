// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api.options

import io.stepfunc.dnp3c.conformance.api.{
  BooleanOption, IntegerOption, OptionGroup, OptionTypes, StringOption, TestOption
}

/**
 * 3.1r1's network tests (section 9): what the DUT's network setup can do, and the addresses of this host the harness
 * uses. Each test runs only where the options say the DUT can do what it tests, including what 3.1r1 requires of every
 * device, so that a device or a test bench without it gets the test skipped, with the option named.
 */
object NetworkOptions extends OptionGroup {

  def prefix: String = "network"
  def description: String = "Network options (3.1r1's section 9)"

  val masterAddress: StringOption = OptionTypes.string(
    this,
    "master.address",
    "",
    "The IP address of this host that 3.1r1's network tests connect from. They need one; and where the DUT runs on " +
      "this host, it must listen on another address, not on every address (0.0.0.0), as the tests connect from port " +
      "20000 of this one"
  )

  val tcp: BooleanOption = OptionTypes.boolean(
    this,
    "tcp",
    false,
    "Run 3.1r1's TCP network tests (9.5.1): they connect to the DUT's port 20000 from network.master.address, and set " +
      "up the DUT's network through the plugin. 3.1r1 requires 9.5.1's Port 20000 test of a device that uses TCP"
  )

  val tcpAnyAddress: BooleanOption = OptionTypes.boolean(
    this,
    "tcp.anyAddress",
    true,
    "Can the DUT accept TCP connections from any IP address? Where it can't, 3.1r1's network tests set it to accept " +
      "network.master.address, and skip the end of 9.5.1's Specific IP Address test"
  )

  val tcpConfigurablePort: BooleanOption = OptionTypes.boolean(
    this,
    "tcp.configurablePort",
    false,
    "Can the DUT listen for TCP connections on a port other than 20000 (network.tcp.otherPort)? 3.1r1's 9.5.1 tests it"
  )

  val tcpOtherPort: IntegerOption = OptionTypes.integer(
    this,
    "tcp.otherPort",
    20001,
    "The port other than 20000 that 3.1r1's 9.5.1 has the DUT listen on, where network.tcp.configurablePort is true",
    Some(1),
    Some(65535)
  )

  val tcpSpecificAddress: BooleanOption = OptionTypes.boolean(
    this,
    "tcp.specificAddress",
    false,
    "Can the DUT accept TCP connections from one IP address only? 3.1r1's 9.5.1 tests it, with " +
      "network.otherMasterAddress"
  )

  val otherMasterAddress: StringOption = OptionTypes.string(
    this,
    "otherMasterAddress",
    "192.0.2.1",
    "An IP address that isn't this host's, which 3.1r1's Specific IP Address tests have the DUT accept, so that it " +
      "refuses the harness"
  )

  val tcpWildcard: BooleanOption = OptionTypes.boolean(
    this,
    "tcp.wildcard",
    false,
    "Can the DUT accept TCP connections from a range of IP addresses given by a wildcard (network.wildcardRange)? " +
      "3.1r1's 9.5.1 tests it, connecting from network.master.address and network.master.wildcardAddress, then " +
      "network.master.outsideAddress"
  )

  val wildcardRange: StringOption = OptionTypes.string(
    this,
    "wildcardRange",
    "",
    "A range of IP addresses, as the DUT takes a wildcard, that holds network.master.address and " +
      "network.master.wildcardAddress but not network.master.outsideAddress, e.g. 127.0.0.*"
  )

  val wildcardAddress: StringOption = OptionTypes.string(
    this,
    "master.wildcardAddress",
    "",
    "Another IP address of this host, in network.wildcardRange, for 3.1r1's Wildcard IP Address tests"
  )

  val outsideAddress: StringOption = OptionTypes.string(
    this,
    "master.outsideAddress",
    "",
    "An IP address of this host outside network.wildcardRange, for 3.1r1's Wildcard IP Address tests"
  )

  val tcpUdpBroadcast: BooleanOption = OptionTypes.boolean(
    this,
    "tcp.udpBroadcast",
    false,
    "Does the DUT, while using TCP, accept broadcasts sent to its port 20000 over UDP? 3.1r1's 9.5.1 tests it, and " +
      "requires it of a device that accepts broadcasts, as IEEE 1815-2012 13.2.3.2 does"
  )

  val tcpMulticast: BooleanOption = OptionTypes.boolean(
    this,
    "tcp.multicast",
    false,
    "Does the DUT, while using TCP, accept broadcasts sent over UDP to a multicast group (network.multicastGroup)? " +
      "3.1r1's 9.5.1 tests it"
  )

  val multicastGroup: StringOption = OptionTypes.string(
    this,
    "multicastGroup",
    "",
    "The multicast group 3.1r1's UDP Multicast test has the DUT join, e.g. 239.0.0.1"
  )

  val udp: BooleanOption = OptionTypes.boolean(
    this,
    "udp",
    false,
    "Can the DUT answer requests over UDP? 3.1r1's UDP Solicited tests (9.5.2) run where it can, connecting to its " +
      "port 20000 over UDP from network.master.address"
  )

  val udpAlone: BooleanOption = OptionTypes.boolean(
    this,
    "udp.alone",
    true,
    "Can the DUT use UDP alone? Where it can't, 3.1r1's UDP tests set it to use TCP and UDP at once"
  )

  val udpAnyAddress: BooleanOption = OptionTypes.boolean(
    this,
    "udp.anyAddress",
    true,
    "Can the DUT accept UDP requests from any IP address? Where it can't, 3.1r1's UDP tests set it to accept " +
      "network.master.address"
  )

  val udpOriginatingPort: BooleanOption = OptionTypes.boolean(
    this,
    "udp.respondToOriginatingPort",
    false,
    "Can the DUT send its UDP responses to the port each request came from? 3.1r1 requires it of a device that " +
      "answers over UDP, and runs its UDP tests (9.5.2) only where it can"
  )

  val udpRespondTo20000: BooleanOption = OptionTypes.boolean(
    this,
    "udp.respondTo20000",
    false,
    "Can the DUT send its UDP responses to port 20000, whatever port each request came from? 3.1r1 requires it of a " +
      "device that answers over UDP, and tests it in 9.5.2"
  )

  val udpConfigurablePort: BooleanOption = OptionTypes.boolean(
    this,
    "udp.configurablePort",
    false,
    "Can the DUT listen for UDP requests on a port other than 20000 (network.udp.otherPort)? 3.1r1's 9.5.2 tests it"
  )

  val udpOtherPort: IntegerOption = OptionTypes.integer(
    this,
    "udp.otherPort",
    20001,
    "The port other than 20000 that 3.1r1's 9.5.2 has the DUT listen on, where network.udp.configurablePort is true",
    Some(1),
    Some(65535)
  )

  val udpSpecificAddress: BooleanOption = OptionTypes.boolean(
    this,
    "udp.specificAddress",
    false,
    "Can the DUT accept UDP requests from one IP address only? 3.1r1's 9.5.2 tests it, with network.otherMasterAddress"
  )

  val udpWildcard: BooleanOption = OptionTypes.boolean(
    this,
    "udp.wildcard",
    false,
    "Can the DUT accept UDP requests from a range of IP addresses given by a wildcard (network.wildcardRange)? 3.1r1's " +
      "9.5.2 tests it, as 9.5.1 does for TCP"
  )

  def options: List[TestOption] = List(
    masterAddress,
    tcp,
    tcpAnyAddress,
    tcpConfigurablePort,
    tcpOtherPort,
    tcpSpecificAddress,
    otherMasterAddress,
    tcpWildcard,
    tcpUdpBroadcast,
    tcpMulticast,
    multicastGroup,
    udp,
    udpAlone,
    udpAnyAddress,
    udpOriginatingPort,
    udpRespondTo20000,
    udpConfigurablePort,
    udpOtherPort,
    udpSpecificAddress,
    udpWildcard,
    wildcardRange,
    wildcardAddress,
    outsideAddress
  )
}
