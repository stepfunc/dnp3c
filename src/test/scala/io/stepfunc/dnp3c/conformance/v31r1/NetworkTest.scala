// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import java.net.{ ConnectException, InetSocketAddress }

import scala.collection.mutable

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.api.{ Options, ProcedureDefinition }
import io.stepfunc.dnp3c.conformance.api.options.NetworkOptions
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.Message
import io.stepfunc.dnp3c.dnp3.link.{ Control, LinkFrame }
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// 3.1r1's 9.5.1, with the harness at 10.0.0.1 (and 10.0.0.2 in the wildcard range, 10.0.1.1 outside it)
@RunWith(classOf[JUnitRunner])
class NetworkTest extends AnyFunSuite with Matchers {

  /**
   * Listens as the plugin sets it up, accepting what its setup allows: over TCP it accepts connections, and over UDP it
   * answers datagrams, to the port they came from or to the fixed port its setup gives. It answers REQUEST LINK STATUS
   * on the harness's connection, and a read with IIN1.0 set after a broadcast to its port 20000 over UDP, or to the
   * multicast group its setup gives. A DUT that ignoresSetup keeps using TCP on port 20000, for any master.
   */
  private class Dut(ignoresSetup: Boolean = false, ignoresBroadcasts: Boolean = false)
      extends RespondingDriver {
    private var setup = NetworkSetup(NetworkMode.Tcp, 20000, AllowedMasters.Any)
    val setups: mutable.ListBuffer[NetworkSetup] = mutable.ListBuffer.empty
    val connections: mutable.ListBuffer[InetSocketAddress] = mutable.ListBuffer.empty
    var restored = 0
    // the harness's connection, which the real driver opens again on the next write after a close
    private var current: Option[Connection] = None
    private var pending: Option[LinkFrame] = None
    private var broadcast = false

    override protected val testPlugin: DefaultIntegrationPlugin = new DefaultIntegrationPlugin {
      override def configureNetwork(reporter: PluginReporter, s: NetworkSetup): Unit = {
        setups += s
        if (!ignoresSetup) setup = s
      }
      override def restoreNetwork(reporter: PluginReporter): Unit = restored += 1
      override def verifyDisconnectNoticed(reporter: CheckReporter): Option[DeferredCheck] = None
    }

    private def bound(c: Connection): InetSocketAddress =
      new InetSocketAddress(c.local.getAddress, if (c.local.getPort == 0) 50000 else c.local.getPort)

    private def allowed(c: Connection): Boolean = {
      val address = c.local.getAddress.getHostAddress
      c.remote.getPort == setup.port &&
      (setup.allowedMasters match {
        case AllowedMasters.Any => true
        case AllowedMasters.Address(a) => a == address
        case AllowedMasters.Wildcard(w) => address.startsWith(w.stripSuffix("*"))
      })
    }

    private def answers(c: Connection): Boolean = c match {
      case _: TcpConnection => setup.mode != NetworkMode.Udp && allowed(c)
      case _: UdpConnection =>
        setup.mode != NetworkMode.Tcp && allowed(c) &&
        (setup.udpResponses match {
          case UdpDestination.OriginatingPort => true
          case UdpDestination.Fixed(p) => bound(c).getPort == p
        })
    }

    override def connect(c: Connection, keep: Boolean): InetSocketAddress = {
      c match {
        case t: TcpConnection if !answers(t) => throw new ConnectException("Connection refused")
        case _ =>
      }
      current = Some(c)
      connections += bound(c)
      bound(c)
    }

    override def probe(c: TcpConnection): ProbeResult = if (answers(c)) ProbeResult.Answered else ProbeResult.Refused

    override def sendDatagrams(
        remote: InetSocketAddress,
        local: InetSocketAddress,
        message: Message,
        options: LinkOverride): Unit = {
      val address = remote.getAddress
      val reaches =
        if (address.isMulticastAddress) setup.multicastGroup.contains(address.getHostAddress) else true
      if (!ignoresBroadcasts && reaches && remote.getPort == 20000 && options.destination.contains(UInt16LE(0xFFFF)))
        broadcast = true
    }

    override def writeFrame(frame: LinkFrame): Unit =
      if (current.exists(answers) && frame.header.ctrl.toByte == 0xC9.toByte)
        pending = Some(LinkFrame.headerOnly(Control(0x0B.toByte), UInt16LE(1), UInt16LE(1024)))

    override def readFrame(timeout: Option[Int]): Option[LinkFrame] = {
      val frame = pending
      pending = None
      frame.orElse(timedOut(timeout))
    }

    override def writeMessage(message: Message, options: LinkOverride): Unit = {
      val seq = message.ctrl.seq.value.toInt
      enqueue("%02X 81 %02X 00".format(0xC0 | seq, if (broadcast) 1 else 0))
      broadcast = false
    }

  }

  private val bench = Map(
    NetworkOptions.masterAddress.id -> "10.0.0.1",
    NetworkOptions.tcp.id -> "true",
    NetworkOptions.tcpConfigurablePort.id -> "true",
    NetworkOptions.tcpSpecificAddress.id -> "true",
    NetworkOptions.tcpWildcard.id -> "true",
    NetworkOptions.wildcardRange.id -> "10.0.0.*",
    NetworkOptions.wildcardAddress.id -> "10.0.0.2",
    NetworkOptions.outsideAddress.id -> "10.0.1.1",
    NetworkOptions.tcpUdpBroadcast.id -> "true",
    NetworkOptions.tcpMulticast.id -> "true",
    NetworkOptions.multicastGroup.id -> "239.0.0.1",
    NetworkOptions.udp.id -> "true",
    NetworkOptions.udpOriginatingPort.id -> "true",
    NetworkOptions.udpRespondTo20000.id -> "true",
    NetworkOptions.udpConfigurablePort.id -> "true",
    NetworkOptions.udpSpecificAddress.id -> "true",
    NetworkOptions.udpWildcard.id -> "true"
  )

  private def run(procedure: ProcedureDefinition, dut: Dut, settings: Map[String, String] = Map()): Boolean =
    Run.passes(procedure, dut, bench ++ settings)

  test("9.5.1.1: connections from port 20000, then from another port, which is noted") {
    val dut = new Dut
    run(Network.tcpPort20000, dut) should equal(true)
    dut.setups.toList should equal(List(NetworkSetup(NetworkMode.Tcp, 20000, AllowedMasters.Any)))
    dut.connections.map(_.getPort).toList should equal(List(20000, 50000))
    dut.reporter.notes should contain("connected from source port 50000")
    dut.restored should equal(1)
  }

  test("9.5.1.1: a DUT that can't accept any master is set to accept the harness's address") {
    val dut = new Dut
    run(Network.tcpPort20000, dut, Map(NetworkOptions.tcpAnyAddress.id -> "false")) should equal(true)
    dut.setups.head.allowedMasters should equal(AllowedMasters.Address("10.0.0.1"))
  }

  test("9.5.1.2: a DUT on the other port refuses port 20000; one still on port 20000 fails, and is restored") {
    val dut = new Dut
    run(Network.tcpOtherPort, dut) should equal(true)
    dut.setups.map(_.port).toList should equal(List(20001, 20000))
    dut.connections.size should equal(1)

    val stuck = new Dut(ignoresSetup = true)
    run(Network.tcpOtherPort, stuck) should equal(false)
    stuck.restored should equal(1)
  }

  test("9.5.1.3: a DUT that accepts another master refuses the harness, then accepts it once set to") {
    val dut = new Dut
    run(Network.tcpSpecificAddress, dut) should equal(true)
    dut.setups.map(_.allowedMasters).toList should
      equal(List(AllowedMasters.Address("192.0.2.1"), AllowedMasters.Address("10.0.0.1")))
    run(Network.tcpSpecificAddress, new Dut(ignoresSetup = true)) should equal(false)
  }

  test("9.5.1.3: without any-address support, the test ends after the refusal") {
    val dut = new Dut
    run(Network.tcpSpecificAddress, dut, Map(NetworkOptions.tcpAnyAddress.id -> "false")) should equal(true)
    dut.setups.size should equal(1)
    dut.connections should be(empty)
  }

  test("9.5.1.4: connections from both addresses in the range, and none from outside it") {
    val dut = new Dut
    run(Network.tcpWildcard, dut) should equal(true)
    dut.connections.map(_.getAddress.getHostAddress).toList should equal(List("10.0.0.1", "10.0.0.2"))
    run(Network.tcpWildcard, new Dut(ignoresSetup = true)) should equal(false)
  }

  test("9.5.1.5: a broadcast over UDP to port 20000 sets IIN1.0 in the next poll over TCP") {
    run(Network.udpBroadcast, new Dut) should equal(true)
    run(Network.udpBroadcast, new Dut(ignoresBroadcasts = true)) should equal(false)
  }

  test("9.5.1.6: a broadcast to the multicast group the DUT joined sets IIN1.0") {
    val dut = new Dut
    run(Network.udpMulticast, dut) should equal(true)
    dut.setups.head.multicastGroup should equal(Some("239.0.0.1"))
    run(Network.udpMulticast, new Dut(ignoresSetup = true)) should equal(false)
  }

  test("9.5.2.1: a link status over UDP, alone or with TCP where the DUT can't use UDP alone") {
    val dut = new Dut
    run(Network.udpPort20000, dut) should equal(true)
    dut.setups.toList should
      equal(List(NetworkSetup(NetworkMode.Udp, 20000, AllowedMasters.Any, UdpDestination.OriginatingPort)))
    val both = new Dut
    run(Network.udpPort20000, both, Map(NetworkOptions.udpAlone.id -> "false")) should equal(true)
    both.setups.head.mode should equal(NetworkMode.Both)
    run(Network.udpPort20000, new Dut(ignoresSetup = true)) should equal(false)
  }

  test("9.5.2.2 to 9.5.2.4: the DUT answers where its setup lets it, and only there") {
    for (p <- List(Network.udpOtherPort, Network.udpSpecificAddress, Network.udpWildcard)) {
      val dut = new Dut
      run(p, dut) should equal(true)
      dut.restored should equal(1)
      run(p, new Dut(ignoresSetup = true)) should equal(false)
    }
  }

  test("9.5.2.5: the harness sends from port 20000, where the DUT's responses go") {
    val dut = new Dut
    run(Network.udpResponsesTo20000, dut) should equal(true)
    dut.connections.map(_.getPort).toList should equal(List(20000))
    dut.setups.head.udpResponses should equal(UdpDestination.Fixed(20000))
  }

  test("without the options, each test is skipped; every UDP Solicited test needs responses to the originating port") {
    val all = List(
      Network.tcpPort20000,
      Network.tcpOtherPort,
      Network.tcpSpecificAddress,
      Network.tcpWildcard,
      Network.udpBroadcast,
      Network.udpMulticast,
      Network.udpPort20000,
      Network.udpOtherPort,
      Network.udpSpecificAddress,
      Network.udpWildcard,
      Network.udpResponsesTo20000
    )
    for (p <- all) p.disabledBy(Options.from(Map())) should not equal None
    for (p <- all) p.disabledBy(Options.from(bench)) should equal(None)
    val noOriginating = Options.from(bench + (NetworkOptions.udpOriginatingPort.id -> "false"))
    Network.udpPort20000.disabledBy(noOriginating) should not equal None
    Network.udpResponsesTo20000.disabledBy(noOriginating) should not equal None
  }

  // what the procedures need, of those that would run, each problem once
  private def problems(settings: Map[String, String], procedures: ProcedureDefinition*): List[String] = {
    val options = Options.from(settings)
    procedures.toList.filter(_.disabledBy(options).isEmpty).flatMap(_.configurationProblems(options)).distinct
      .map(_.key)
  }

  test("a network test that runs needs an address of this host, and the wildcard test its addresses and range") {
    problems(bench, Network.tcpPort20000, Network.tcpWildcard) should equal(Nil)
    problems(bench - NetworkOptions.masterAddress.id, Network.tcpPort20000) should
      equal(List(NetworkOptions.masterAddress.id))
    problems(bench + (NetworkOptions.masterAddress.id -> "0.0.0.0"), Network.tcpPort20000) should
      equal(List(NetworkOptions.masterAddress.id))
    problems(bench + (NetworkOptions.masterAddress.id -> "fd00::1"), Network.tcpPort20000) should equal(Nil)
    problems(bench - NetworkOptions.wildcardRange.id - NetworkOptions.outsideAddress.id, Network.tcpWildcard) should
      equal(List(NetworkOptions.outsideAddress.id, NetworkOptions.wildcardRange.id))
    problems(bench + (NetworkOptions.wildcardAddress.id -> "10.0.0.1"), Network.tcpWildcard) should
      equal(List(NetworkOptions.wildcardAddress.id))
    problems(bench + (NetworkOptions.tcpOtherPort.id -> "20000"), Network.tcpOtherPort) should
      equal(List(NetworkOptions.tcpOtherPort.id))
    problems(bench + (NetworkOptions.udpOtherPort.id -> "20000"), Network.udpOtherPort) should
      equal(List(NetworkOptions.udpOtherPort.id))
    problems(bench + (NetworkOptions.multicastGroup.id -> "10.0.0.9"), Network.udpMulticast) should
      equal(List(NetworkOptions.multicastGroup.id))
    problems(bench - NetworkOptions.wildcardRange.id, Network.udpWildcard) should
      equal(List(NetworkOptions.wildcardRange.id))
    // tests that won't run need nothing
    problems(Map(), Network.tcpPort20000, Network.tcpWildcard) should equal(Nil)
  }
}
