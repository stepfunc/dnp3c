// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import java.net.InetSocketAddress

import scala.collection.mutable

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.api.{ Options, ProcedureDefinition, TestProcedure }
import io.stepfunc.dnp3c.conformance.api.options.NetworkOptions
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.{ AppFunction, Message, Unsolicited }
import io.stepfunc.dnp3c.dnp3.link.{ FrameValidator, LinkFrame }
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// 3.1r1's 9.5.3 and 9.5.4, with the harness at 10.0.0.1
@RunWith(classOf[JUnitRunner])
class UnsolicitedNetworkTest extends AnyFunSuite with Matchers {

  /**
   * Sends unsolicited responses as its setup says: when it restarts, a null one over UDP to the master's null port;
   * once a master enables them, one with an event for the events generated, over TCP while a TCP connection is open,
   * else over UDP to the data destination: a fixed port, or the one the null was confirmed from. Each is sent again
   * until confirmed. It answers requests over the connection they came on, over UDP to the setup's destination. A DUT
   * that dataTo20000 sends everything after the null to port 20000, and one that udpAfterTcp keeps sending over UDP
   * after a TCP connection is made.
   */
  private class Dut(dataTo20000: Boolean = false, udpAfterTcp: Boolean = false, queuedAtStart: Int = 0)
      extends RespondingDriver {
    private var setup = NetworkSetup(NetworkMode.Tcp, 20000, AllowedMasters.Any)
    var restored = 0
    // the harness's connections, by the ports they're bound to
    private var current: Option[Connection] = None
    private val open = mutable.Map.empty[Connection, Int]
    private var tcpOpen = false
    // the unsolicited response waiting for a confirmation: (sequence, null), and the port the null was confirmed from
    private var unconfirmed: Option[(Int, Boolean)] = None
    private var confirmedFrom: Option[Int] = None
    private var enabled = false
    private var seq = 0
    private var udpSent = 0
    // retries of the null already waiting when the restart ends, which a quick read gets
    private var queued = 0

    override protected val testPlugin: DefaultIntegrationPlugin = new DefaultIntegrationPlugin {
      override def configureNetwork(reporter: PluginReporter, s: NetworkSetup): Unit = setup = s
      override def restoreNetwork(reporter: PluginReporter): Unit = restored += 1
      override def cyclePower(reporter: PluginReporter): Unit = {
        queued = queuedAtStart
        tcpOpen = false
        enabled = false
        confirmedFrom = None
        unconfirmed = Some((seq, true))
      }
      override def generateUnsolicitedEvents(reporter: PluginReporter, timing: ActionTiming): ActionResult = {
        if (enabled) {
          seq = (seq + 1) % 16
          unconfirmed = Some((seq, false))
        }
        ActionResult.Performed
      }
    }

    private def port(c: Connection): Int = open.getOrElse(c, if (c.local.getPort == 0) 50000 else c.local.getPort)

    // where the DUT sends a UDP message: the null to its port, the rest to the data destination
    private def destination(isNull: Boolean): Int = setup.unsolicited match {
      case Some(u) if isNull => u.nullPort
      case _ if dataTo20000 => 20000
      case Some(u) =>
        u.data match {
          case UdpDestination.Fixed(p) => p
          case UdpDestination.OriginatingPort => confirmedFrom.getOrElse(-1)
        }
      case None => -1
    }

    // whether the harness's current connection gets a message the DUT sends unsolicited, or in answer to it
    private def reaches(isNull: Boolean, solicited: Boolean): Boolean = current.exists {
      case _: TcpConnection => tcpOpen
      case c: UdpConnection =>
        val overUdp = setup.mode != NetworkMode.Tcp && (solicited || !tcpOpen || udpAfterTcp)
        overUdp &&
        (if (solicited) setup.udpResponses match {
           case UdpDestination.Fixed(p) if !dataTo20000 => port(c) == p
           case _ if dataTo20000 => port(c) == 20000
           case UdpDestination.OriginatingPort => true
         }
         else port(c) == destination(isNull))
    }

    override def connect(c: Connection, keep: Boolean): InetSocketAddress = {
      if (!keep) current.foreach(open.remove)
      open(c) = port(c)
      current = Some(c)
      c match {
        case _: TcpConnection => tcpOpen = true
        case _ =>
      }
      new InetSocketAddress(c.local.getAddress, port(c))
    }

    override def drainDatagrams(c: UdpConnection): Int = {
      val n = udpSent
      udpSent = 0
      n
    }

    override def readFrame(timeout: Option[Int]): Option[LinkFrame] = timedOut(timeout)

    override def writeMessage(message: Message, options: LinkOverride): Unit = {
      val s = message.ctrl.seq.value.toInt
      message.function match {
        case AppFunction.confirm =>
          if (unconfirmed.exists(_._1 == s)) {
            if (unconfirmed.exists(_._2)) confirmedFrom = current.map(port)
            unconfirmed = None
          }
        case f =>
          if (f == AppFunction.enableUnsolicited) enabled = true
          if (reaches(isNull = false, solicited = true))
            enqueue("%02X 81 00 00".format(0xC0 | s))
      }
    }

    override def readMessage(
        unsolicited: Unsolicited,
        validator: FrameValidator,
        linkOverride: LinkOverride,
        timeoutMs: Option[Int]): Option[Message] =
      if (unsolicited.value) {
        // each read gets the response, or its retry, where it goes, but a quick one only what's waiting; over UDP,
        // the retries are counted
        // copies of the null already sent wait to be read, even once it's confirmed
        if (queued > 0 && reaches(isNull = true, solicited = false)) {
          queued -= 1
          return Some(Message.parse(Hex.from("%02X 82 80 00".format(0xF0 | seq))))
        }
        if (timeoutMs.exists(_ < 1000)) return timedOut(timeoutMs)
        unconfirmed match {
          case Some((s, isNull)) =>
            if (setup.mode != NetworkMode.Tcp && (!tcpOpen || udpAfterTcp)) udpSent += 1
            if (reaches(isNull, solicited = false)) {
              val body = if (isNull) "80 00" else "00 00 02 01 17 01 00 01"
              Some(Message.parse(Hex.from("%02X 82 %s".format(0xF0 | s, body))))
            } else timedOut(timeoutMs)
          case None => timedOut(timeoutMs)
        }
      } else super.readMessage(unsolicited, validator, linkOverride, timeoutMs)
  }

  private val bench = Map(
    NetworkOptions.masterAddress.id -> "10.0.0.1",
    NetworkOptions.tcp.id -> "true",
    NetworkOptions.udp.id -> "true",
    NetworkOptions.udpUnsolicited.id -> "true",
    NetworkOptions.udpUnsolicitedTo20000.id -> "true",
    NetworkOptions.udpUnsolicitedNullPort.id -> "true",
    NetworkOptions.udpUnsolicitedToOriginatingPort.id -> "true",
    NetworkOptions.udpFixedOtherPort.id -> "true",
    NetworkOptions.udpAndTcp.id -> "true"
  )

  private def run(procedure: ProcedureDefinition, dut: Dut): Boolean = {
    val passed = Run.passes(procedure, dut, bench)
    dut.restored should equal(1)
    passed
  }

  test("9.5.3.1 and 9.5.3.2: the null comes to the port set up, and once confirmed, not again") {
    run(Network.unsolicitedNull20000, new Dut) should equal(true)
    run(Network.unsolicitedNullOtherPort, new Dut) should equal(true)
  }

  test("retries of the null that queued up during the restart are read, and the newest confirmed") {
    run(Network.unsolicitedNull20000, new Dut(queuedAtStart = 3)) should equal(true)
    run(Network.unsolicitedData20000, new Dut(queuedAtStart = 3)) should equal(true)
    run(Network.unsolicitedUdpAndTcp, new Dut(queuedAtStart = 3)) should equal(true)
  }

  test("the null tests need the null routed to port 20000 one way or the other, and the other ports other than 20000") {
    val without20000 = Options.from(bench + (NetworkOptions.udpUnsolicitedTo20000.id -> "false"))
    Network.unsolicitedNull20000.disabledBy(without20000) should equal(None)
    val neither = Options.from(
      bench + (NetworkOptions.udpUnsolicitedTo20000.id -> "false") +
        (NetworkOptions.udpUnsolicitedToOriginatingPort.id -> "false"))
    Network.unsolicitedNull20000.disabledBy(neither) should not equal None
    def problems(settings: Map[String, String], p: ProcedureDefinition) =
      Network.configurationProblems(Options.from(bench ++ settings), List(TestProcedure("id", "t", p))).map(_.key)
    problems(Map(NetworkOptions.udpUnsolicitedPort.id -> "20000"), Network.unsolicitedNullOtherPort) should
      equal(List(NetworkOptions.udpUnsolicitedPort.id))
    problems(Map(NetworkOptions.udpOtherPort.id -> "20000"), Network.unsolicitedDataFixedPort) should
      equal(List(NetworkOptions.udpOtherPort.id))
  }

  test("9.5.3.3 and 9.5.3.5: every message comes to the port set up") {
    run(Network.unsolicitedData20000, new Dut) should equal(true)
    run(Network.unsolicitedDataFixedPort, new Dut) should equal(true)
    run(Network.unsolicitedDataFixedPort, new Dut(dataTo20000 = true)) should equal(false)
  }

  test("9.5.3.4: after the null, every message comes to the port it was confirmed from") {
    run(Network.unsolicitedDataOriginatingPort, new Dut) should equal(true)
    run(Network.unsolicitedDataOriginatingPort, new Dut(dataTo20000 = true)) should equal(false)
  }

  test("9.5.4: unsolicited responses move to TCP once it's connected, and UDP requests are still answered") {
    run(Network.unsolicitedUdpAndTcp, new Dut) should equal(true)
    run(Network.unsolicitedUdpAndTcp, new Dut(udpAfterTcp = true)) should equal(false)
  }
}
