// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import java.net.{ ConnectException, InetSocketAddress }

import scala.collection.mutable
import scala.util.Try

import io.stepfunc.dnp3c.conformance.api.{ Options, ProcedureDefinition, TestProcedure }
import io.stepfunc.dnp3c.conformance.api.options.NetworkOptions
import io.stepfunc.dnp3c.dnp3._
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
   * Listens as the plugin sets it up, accepting what its setup allows and answering REQUEST LINK STATUS on its
   * connection; a DUT that ignoresSetup keeps listening on port 20000 for any master
   */
  private class Dut(ignoresSetup: Boolean = false) extends StubDriver(StubDriver.reporter()) {
    private var setup = NetworkSetup(NetworkMode.Tcp, 20000, AllowedMasters.Any)
    val setups: mutable.ListBuffer[NetworkSetup] = mutable.ListBuffer.empty
    val connections: mutable.ListBuffer[InetSocketAddress] = mutable.ListBuffer.empty
    var restored = 0
    private var connected = false
    private var pending: Option[LinkFrame] = None

    val plugin: DefaultIntegrationPlugin = new DefaultIntegrationPlugin {
      override def configureNetwork(reporter: PluginReporter, s: NetworkSetup): Unit = {
        setups += s
        if (!ignoresSetup) setup = s
      }
      override def restoreNetwork(reporter: PluginReporter): Unit = restored += 1
      override def verifyDisconnectNoticed(reporter: CheckReporter): Option[DeferredCheck] = None
    }
    override val reporter: ProcedureReporter = StubDriver.reporter(plugin)

    private def accepts(c: TcpConnection): Boolean = {
      val address = c.local.getAddress.getHostAddress
      c.remote.getPort == setup.port &&
      (setup.allowedMasters match {
        case AllowedMasters.Any => true
        case AllowedMasters.Address(a) => a == address
        case AllowedMasters.Wildcard(w) => address.startsWith(w.stripSuffix("*"))
      })
    }

    override def connect(c: TcpConnection): InetSocketAddress =
      if (accepts(c)) {
        connected = true
        val local = new InetSocketAddress(c.local.getAddress, if (c.local.getPort == 0) 50000 else c.local.getPort)
        connections += local
        local
      } else throw new ConnectException("Connection refused")

    override def probe(c: TcpConnection): ProbeResult = if (accepts(c)) ProbeResult.Answered else ProbeResult.Refused

    override def close(): Unit = connected = false

    override def writeFrame(frame: LinkFrame): Unit =
      if (connected && frame.header.ctrl.toByte == 0xC9.toByte)
        pending = Some(LinkFrame.headerOnly(Control(0x0B.toByte), UInt16LE(1), UInt16LE(1024)))

    override def readFrame(timeout: Option[Int]): Option[LinkFrame] = {
      val frame = pending
      pending = None
      frame.orElse(timedOut(timeout))
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
    NetworkOptions.outsideAddress.id -> "10.0.1.1"
  )

  // runs the steps, then the cleanup, as the runner does
  private def run(procedure: ProcedureDefinition, dut: Dut, settings: Map[String, String] = Map()): Boolean = {
    val config = procedure.configure(Options.from(bench ++ settings))
    val passed = Try(config.steps.foreach(s => s.step.run(dut).foreach(_.check()))).isSuccess
    config.cleanup.foreach(s => s.step.run(dut).foreach(_.check()))
    passed
  }

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

  test("without the options, each test is skipped") {
    for (p <- List(Network.tcpPort20000, Network.tcpOtherPort, Network.tcpSpecificAddress, Network.tcpWildcard))
      p.disabledBy(Options.from(Map())) should not equal None
  }

  private def problems(settings: Map[String, String], procedures: ProcedureDefinition*): List[String] =
    Network.configurationProblems(
      Options.from(settings),
      procedures.map(p => TestProcedure("id", "title", p))).map(_.key)

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
    // tests that won't run need nothing
    problems(Map(), Network.tcpPort20000, Network.tcpWildcard) should equal(Nil)
  }
}
