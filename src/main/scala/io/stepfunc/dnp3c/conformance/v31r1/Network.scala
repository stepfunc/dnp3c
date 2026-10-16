// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import java.net.{ ConnectException, InetAddress, InetSocketAddress, SocketTimeoutException }

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{
  ChannelOptions, NetworkOptions, SupportOptions, TestOptions, UnsolicitedOptions
}
import io.stepfunc.dnp3c.conformance.api.options.NetworkOptions.isAddress
import io.stepfunc.dnp3c.conformance.common.LinkSteps.{ expectFrame, sendLinkControl, withAnyControl }
import io.stepfunc.dnp3c.conformance.common.GenericSteps.{
  expectAtLeastOneResponse, expectNoUnsolicitedResponse, expectSingleResponse, expectUnsolicitedResponse, send
}
import io.stepfunc.dnp3c.conformance.common.GenericSteps.validateUnsolicitedResponse
import io.stepfunc.dnp3c.conformance.common.Validations.{ isNull, without }
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.{ AppFunction, IIN, Message, Sequence, Unsolicited }
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group60Var2, Group60Var3, Group60Var4 }
import io.stepfunc.dnp3c.dnp3.link.FrameValidator
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE

/**
 * 3.1r1's section 9, network tests. Each sets up the DUT's network through the plugin (configureNetwork), in full, and
 * restores its usual setup at the end, however the test ends: 3.1r1 runs the tests in sequence, each keeping the setup
 * the one before left, where dnp3c runs each alone.
 *
 * The harness connects from network.master.address, the DUT being at tcp.device.address, on port 20000 as 3.1r1 asks
 * rather than at tcp.device.port. A connection the DUT shouldn't accept passes when the DUT refuses it, doesn't accept
 * it within the response timeout, or accepts it and then closes it without answering a REQUEST LINK STATUS (a DUT that
 * checks the master's address once connected); it fails when the DUT keeps it open.
 */
object Network {

  private val port = 20000

  // what a network test needs configured: an address of this host to connect from, and those the test uses
  private def problems(options: OptionSource, test: Test): List[ConfigProblem] = {
    def address(option: StringOption, why: String): Option[ConfigProblem] = {
      val value = options.get(option)
      Option.when(!isAddress(value))(ConfigProblem(option.id, value, s"needs an IP address of this host, $why"))
    }
    val wildcard = test == tcpWildcard || test == udpWildcard
    val group = options.get(NetworkOptions.multicastGroup)
    List(
      address(NetworkOptions.masterAddress, "which 3.1r1's network tests connect from"),
      Option.when(wildcard)(address(NetworkOptions.wildcardAddress, "in network.wildcardRange")).flatten,
      Option.when(wildcard)(address(NetworkOptions.outsideAddress, "outside network.wildcardRange")).flatten,
      Option.when(wildcard && distinct(options).nonEmpty)(
        ConfigProblem(
          NetworkOptions.wildcardAddress.id,
          options.get(NetworkOptions.wildcardAddress),
          s"must differ from ${distinct(options).mkString(" and ")}, as 3.1r1's Wildcard IP Address tests connect from " +
            "each in turn"
        )),
      Option.when(wildcard && options.get(NetworkOptions.wildcardRange).isEmpty)(
        ConfigProblem(NetworkOptions.wildcardRange.id, "", "required by 3.1r1's Wildcard IP Address tests")),
      Option.when(test == tcpOtherPort && options.get(NetworkOptions.tcpOtherPort) == port)(
        ConfigProblem(NetworkOptions.tcpOtherPort.id, port.toString, "must be a port other than 20000")),
      Option.when(
        (test == udpOtherPort || test == unsolicitedDataFixedPort) &&
          options.get(NetworkOptions.udpOtherPort) == port)(
        ConfigProblem(NetworkOptions.udpOtherPort.id, port.toString, "must be a port other than 20000")),
      Option.when(test == unsolicitedNullOtherPort && options.get(NetworkOptions.udpUnsolicitedPort) == port)(
        ConfigProblem(NetworkOptions.udpUnsolicitedPort.id, port.toString, "must be a port other than 20000")),
      Option.when(test == udpMulticast && NetworkOptions.group(options).isEmpty)(
        ConfigProblem(NetworkOptions.multicastGroup.id, group, "needs a multicast group's IP address, e.g. 239.0.0.1"))
    ).flatten
  }

  // the addresses the wildcard tests use that the wildcard address is the same as
  private def distinct(options: OptionSource): List[String] = {
    val address = options.get(NetworkOptions.wildcardAddress)
    List(NetworkOptions.masterAddress, NetworkOptions.outsideAddress)
      .filter(o => address.nonEmpty && options.get(o) == address)
      .map(_.id)
  }

  /** a section 9 test: restores the DUT's network once it ends */
  abstract class Test extends ProcedureDefinition {
    override protected def cleanup(options: OptionSource): Iterable[LabeledStep] =
      List(Label("cleanup") --> restore)
    override def configurationProblems(options: OptionSource): List[ConfigProblem] = problems(options, this)
  }

  private def dut(options: OptionSource, port: Int = port): InetSocketAddress =
    new InetSocketAddress(options.get(ChannelOptions.host), port)

  private def from(address: String, port: Int = 0): InetSocketAddress = new InetSocketAddress(address, port)

  private def master(options: OptionSource): String = options.get(NetworkOptions.masterAddress)

  // what the DUT accepts when a test doesn't care: any master, if it can
  private def anyMaster(options: OptionSource): AllowedMasters =
    if (options.get(NetworkOptions.tcpAnyAddress)) AllowedMasters.Any else AllowedMasters.Address(master(options))

  private def tcp(port: Int, allowed: AllowedMasters): NetworkSetup = NetworkSetup(NetworkMode.Tcp, port, allowed)

  // the masters a UDP test accepts when it doesn't care: any, if the DUT can
  private def anyUdpMaster(options: OptionSource): AllowedMasters =
    if (options.get(NetworkOptions.udpAnyAddress)) AllowedMasters.Any else AllowedMasters.Address(master(options))

  // UDP as the main method, alone if the DUT can, else with TCP
  private def udp(
      options: OptionSource,
      port: Int,
      allowed: AllowedMasters,
      responses: UdpDestination = UdpDestination.OriginatingPort): NetworkSetup = NetworkSetup(
    if (options.get(NetworkOptions.udpAlone)) NetworkMode.Udp else NetworkMode.Both,
    port,
    allowed,
    responses)

  private def setUp(setup: NetworkSetup): TestStep =
    TestStep(List(s"Set up the DUT's network to ${setup.describe}")) { driver =>
      driver.close()
      driver.reporter.plugin.configureNetwork(driver.reporter.pluginReporter(), setup)
    }

  private val restore: TestStep = TestStep(List("Restore the DUT's usual network setup")) { driver =>
    driver.close()
    driver.reporter.plugin.restoreNetwork(driver.reporter.pluginReporter())
  }

  private def show(address: InetSocketAddress): String = {
    val host = address.getAddress.getHostAddress
    if (address.getPort == 0) s"$host, from a port the system chooses" else s"$host, from port ${address.getPort}"
  }

  /** connects, failing if the DUT doesn't accept; record notes the port connected from */
  private def connect(remote: InetSocketAddress, local: InetSocketAddress, record: Boolean = false): TestStep =
    new TestStep {
      def description: List[Comment] = List(
        s"Issue a TCP connection to the DUT's port ${remote.getPort} from ${show(local)}",
        "Verify that the DUT accepts it")
      def run(driver: TestDriver): List[DeferredCheck] = {
        val connected =
          try driver.connect(TcpConnection(remote, local))
          catch {
            case ex @ (_: ConnectException | _: SocketTimeoutException) =>
              throw new Exception(s"The DUT didn't accept the connection: ${ex.getMessage}", ex)
          }
        driver.reporter.info(s"Connected from ${connected.getAddress.getHostAddress} port ${connected.getPort}")
        if (record) driver.reporter.note(s"connected from source port ${connected.getPort}")
        Nil
      }
    }

  private def notAccepted(remote: InetSocketAddress, local: InetSocketAddress): TestStep = new TestStep {
    def description: List[Comment] = List(
      s"Issue a TCP connection to the DUT's port ${remote.getPort} from ${show(local)}",
      "Verify that the DUT doesn't accept it: it refuses it, doesn't accept it within the response timeout, or closes " +
        "it without answering a REQUEST LINK STATUS"
    )
    def run(driver: TestDriver): List[DeferredCheck] = {
      driver.probe(TcpConnection(remote, local)) match {
        case ProbeResult.Refused => driver.reporter.info("The DUT refused the connection")
        case ProbeResult.TimedOut => driver.reporter.info("The DUT didn't accept the connection")
        case ProbeResult.Closed =>
          driver.reporter.info("The DUT accepted the connection, then closed it without answering")
        case ProbeResult.Silent =>
          throw new Exception("The DUT accepted the connection and kept it open")
        case ProbeResult.Answered =>
          throw new Exception("The DUT accepted the connection and answered a REQUEST LINK STATUS on it")
      }
      Nil
    }
  }

  /** talks to the DUT over UDP from now on, sending from the local address, which alone receives the DUT's datagrams */
  private def udpFrom(remote: InetSocketAddress, local: InetSocketAddress): TestStep = new TestStep {
    def description: List[Comment] = List(
      s"Exchange UDP datagrams with the DUT's port ${remote.getPort} from ${show(local)}, processing only the " +
        "datagrams that come back to that port")
    def run(driver: TestDriver): List[DeferredCheck] = {
      val bound = driver.connect(UdpConnection(remote, local))
      driver.reporter.info(s"Sending from ${bound.getAddress.getHostAddress} port ${bound.getPort}")
      Nil
    }
  }

  // a REQUEST LINK STATUS that nothing answers within the link timeout
  private val noLinkStatus: TestStep = sendLinkControl(0xC9.toByte) +
    TestStep(List("Verify that the DUT doesn't answer within the link timeout")) { driver =>
      driver.readFrame().foreach(f => throw new Exception(s"The DUT answered: $f"))
    }

  private val allStations = UInt16LE(0xFFFF)

  // a broadcast that every device accepts, as IEEE 1815-2012 4.7 requires of any it accepts to its own address
  private def broadcast(remote: InetSocketAddress, local: InetSocketAddress, seq: Sequence): TestStep = new TestStep {
    def description: List[Comment] = List(
      s"Send a write that clears IIN1.7 (g80v1, index 7) to DNP3 destination 0xFFFF, as UDP datagrams to " +
        s"${remote.getAddress.getHostAddress} port ${remote.getPort} from ${show(local)}")
    def run(driver: TestDriver): List[DeferredCheck] = {
      driver.sendDatagrams(remote, local, Requests.clearRestartIIN(seq), LinkOverride.destination(allStations))
      Nil
    }
  }

  private val broadcastIndicated: ResponseValidation = new ResponseValidation {
    def description: List[Comment] = List("Verify that the first response has IIN1.0 (all stations) set")
    def validate(reporter: ProcedureReporter, responses: List[Message]): List[DeferredCheck] = {
      if (!responses.headOption.flatMap(_.iin).exists(iin => (iin & IIN.BROADCAST) == IIN.BROADCAST))
        throw new Exception("Expected IIN1.0 (all stations) set in the response")
      Nil
    }
  }

  private val linkStatus: TestStep =
    sendLinkControl(0xC9.toByte) + expectFrame(withAnyControl(Set(0x0B.toByte, 0x1B.toByte)))

  private val closeGracefully: TestStep = TestStep(List("Close the connection gracefully")) { driver =>
    driver.close()
  }

  private val disconnectNoticed: TestStep =
    PluginCheck("Verify that the DUT noticed the connection close")((plugin, reporter) =>
      plugin.verifyDisconnectNoticed(reporter)).deferrable

  /** 9.5.1's Port 20000: connections from source port 20000, and from another */
  val tcpPort20000: ProcedureDefinition = new Test {
    override protected def requiredOptions: List[OptionCondition] = List(NetworkOptions.tcp)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = List(
      (1 to 3) --> setUp(tcp(port, anyMaster(options))),
      (4 to 5) --> connect(dut(options), from(master(options), port)),
      (6 to 7) --> linkStatus,
      8 --> closeGracefully,
      9 --> disconnectNoticed,
      (10 to 11) --> connect(dut(options), from(master(options)), record = true),
      (12 to 13) --> linkStatus
    )
  }

  /** 9.5.1's Port Other Than 20000 */
  val tcpOtherPort: ProcedureDefinition = new Test {
    override protected def requiredOptions: List[OptionCondition] =
      List(NetworkOptions.tcp, NetworkOptions.tcpConfigurablePort)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val other = options.get(NetworkOptions.tcpOtherPort)
      List(
        (1 to 2) --> setUp(tcp(other, anyMaster(options))),
        (3 to 4) --> notAccepted(dut(options), from(master(options))),
        (5 to 6) --> connect(dut(options, other), from(master(options))),
        (7 to 8) --> linkStatus,
        9 --> setUp(tcp(port, anyMaster(options)))
      )
    }
  }

  /** 9.5.1's Specific IP Address */
  val tcpSpecificAddress: ProcedureDefinition = new Test {
    override protected def requiredOptions: List[OptionCondition] =
      List(NetworkOptions.tcp, NetworkOptions.tcpSpecificAddress)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val other = options.get(NetworkOptions.otherMasterAddress)
      List(
        (1 to 2) --> setUp(tcp(port, AllowedMasters.Address(other))),
        (3 to 4) --> notAccepted(dut(options), from(master(options)))
      ) ++ (
        if (!options.get(NetworkOptions.tcpAnyAddress))
          List(5 -->
            describe("The DUT can't accept connections from any IP address, so the rest of the test is skipped"))
        else
          List(
            6 --> setUp(tcp(port, AllowedMasters.Address(master(options)))),
            (7 to 8) --> connect(dut(options), from(master(options))),
            (9 to 10) --> linkStatus
          )
      )
    }
  }

  /** 9.5.1's Wildcard IP Address */
  val tcpWildcard: ProcedureDefinition = new Test {
    override protected def requiredOptions: List[OptionCondition] = List(NetworkOptions.tcp, NetworkOptions.tcpWildcard)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val wildcard = options.get(NetworkOptions.wildcardAddress)
      val outside = options.get(NetworkOptions.outsideAddress)
      List(
        (1 to 2) --> setUp(tcp(port, AllowedMasters.Wildcard(options.get(NetworkOptions.wildcardRange)))),
        (3 to 4) --> connect(dut(options), from(master(options))),
        (5 to 6) --> linkStatus,
        7 --> closeGracefully,
        8 --> disconnectNoticed,
        9 --> describe(s"Connect from $wildcard, which is also in the range"),
        (10 to 11) --> connect(dut(options), from(wildcard)),
        (12 to 13) --> linkStatus,
        14 --> closeGracefully,
        15 --> disconnectNoticed,
        16 --> describe(s"Connect from $outside, which is outside the range"),
        (17 to 18) --> notAccepted(dut(options), from(outside)),
        19 --> describe(s"Connect from ${master(options)} again")
      )
    }
  }

  /**
   * 9.5.1's UDP broadcast tests: a broadcast over UDP to the DUT's port 20000, or to a multicast group, while a TCP
   * connection is open, from the same address; the next poll over TCP has IIN1.0 set
   */
  private def udpBroadcastWithTcp(gate: BooleanOption, to: OptionSource => InetAddress, multicast: Boolean) = new Test {
    override protected def requiredOptions: List[OptionCondition] = List(NetworkOptions.tcp, gate)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val group = Option.when(multicast)(options.get(NetworkOptions.multicastGroup))
      List(
        1 --> describe(
          "The broadcast is a write that clears IIN1.7, which IEEE 1815-2012 4.7 (Table 4-16) has every device accept " +
            "as a broadcast"),
        2 --> setUp(tcp(port, anyMaster(options)).copy(multicastGroup = group)),
        (3 to 4) --> connect(dut(options), from(master(options))),
        5 --> send(Requests.readClass1230(Sequence.zero)),
        6 --> expectAtLeastOneResponse(Sequence.zero),
        7 --> broadcast(new InetSocketAddress(to(options), port), from(master(options)), Sequence.one),
        8 --> send(Requests.readClass1230(Sequence.two)),
        9 --> expectAtLeastOneResponse(Sequence.two, broadcastIndicated)
      )
    }
  }

  /** 9.5.1's UDP Unicast IP Address with TCP as the Main Communication Method */
  val udpBroadcast: ProcedureDefinition =
    udpBroadcastWithTcp(NetworkOptions.tcpUdpBroadcast, o => InetAddress.getByName(o.get(ChannelOptions.host)), false)

  /** 9.5.1's UDP Multicast IP Address with TCP as the Main Communication Method */
  val udpMulticast: ProcedureDefinition =
    udpBroadcastWithTcp(
      NetworkOptions.tcpMulticast,
      o => InetAddress.getByName(o.get(NetworkOptions.multicastGroup)),
      true)

  // what every UDP Solicited test needs: the DUT answering over UDP, to the port each request came from
  private val udpSolicited: List[OptionCondition] = List(NetworkOptions.udp, NetworkOptions.udpOriginatingPort)

  /** 9.5.2's Port 20000 */
  val udpPort20000: ProcedureDefinition = new Test {
    override protected def requiredOptions: List[OptionCondition] = udpSolicited

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = List(
      (1 to 4) --> setUp(udp(options, port, anyUdpMaster(options))),
      5 --> udpFrom(dut(options), from(master(options))),
      (6 to 7) --> linkStatus
    )
  }

  /** 9.5.2's Port Other Than 20000 */
  val udpOtherPort: ProcedureDefinition = new Test {
    override protected def requiredOptions: List[OptionCondition] =
      udpSolicited :+ OptionCondition(NetworkOptions.udpConfigurablePort)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val other = options.get(NetworkOptions.udpOtherPort)
      List(
        (1 to 2) --> setUp(udp(options, other, anyUdpMaster(options))),
        (3 to 4) --> (udpFrom(dut(options), from(master(options))) + noLinkStatus),
        (5 to 6) --> (udpFrom(dut(options, other), from(master(options))) + linkStatus),
        7 --> setUp(udp(options, port, anyUdpMaster(options)))
      )
    }
  }

  /** 9.5.2's Specific IP Address */
  val udpSpecificAddress: ProcedureDefinition = new Test {
    override protected def requiredOptions: List[OptionCondition] =
      udpSolicited :+ OptionCondition(NetworkOptions.udpSpecificAddress)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = List(
      (1 to 2) --> setUp(udp(options, port, AllowedMasters.Address(options.get(NetworkOptions.otherMasterAddress)))),
      (3 to 4) --> (udpFrom(dut(options), from(master(options))) + noLinkStatus),
      5 --> setUp(udp(options, port, AllowedMasters.Address(master(options)))),
      (6 to 7) --> linkStatus
    )
  }

  /** 9.5.2's Wildcard IP Address */
  val udpWildcard: ProcedureDefinition = new Test {
    override protected def requiredOptions: List[OptionCondition] =
      udpSolicited :+ OptionCondition(NetworkOptions.udpWildcard)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val wildcard = options.get(NetworkOptions.wildcardAddress)
      val outside = options.get(NetworkOptions.outsideAddress)
      List(
        (1 to 2) --> setUp(udp(options, port, AllowedMasters.Wildcard(options.get(NetworkOptions.wildcardRange)))),
        (3 to 4) --> (udpFrom(dut(options), from(master(options))) + linkStatus),
        5 --> describe(s"Send from $wildcard, which is also in the range"),
        (6 to 7) --> (udpFrom(dut(options), from(wildcard)) + linkStatus),
        8 --> describe(s"Send from $outside, which is outside the range"),
        (9 to 10) --> (udpFrom(dut(options), from(outside)) + noLinkStatus),
        11 --> describe(s"Send from ${master(options)} again")
      )
    }
  }

  /**
   * 9.5.2's Responses to Port 20000: the harness sends from port 20000, where it receives, as IEEE 1815-2012
   * 13.2.3.2.2 b) has a master do
   */
  val udpResponsesTo20000: ProcedureDefinition = new Test {
    // without responses to the originating port, 9.5.2.1 step 2 skips every UDP test
    override protected def requiredOptions: List[OptionCondition] =
      udpSolicited :+ OptionCondition(NetworkOptions.udpRespondTo20000)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = List(
      (1 to 3) --> setUp(udp(options, port, anyUdpMaster(options), UdpDestination.Fixed(port))),
      4 --> udpFrom(dut(options), from(master(options), port)),
      (5 to 6) --> linkStatus
    )
  }

  // ---- 9.5.3 and 9.5.4: unsolicited responses over UDP ----

  // a retry of an unconfirmed unsolicited response comes within the confirm timeout, give or take the tolerance
  private def retryWindow(options: OptionSource): Option[Int] =
    Some(options.get(UnsolicitedOptions.unsolicitedConfirmTimeoutMs) + options.get(TestOptions.timingToleranceMs))

  private val withEvents = MessageValidation.check("Verify that the response reports events") { m =>
    if (m.headers.isEmpty) throw new Exception("The unsolicited response reports no events")
  }

  private def expectNull(autoConfirm: AutoConfirm, timeoutMs: Option[Int] = None): TestStep =
    expectUnsolicitedResponse(autoConfirm, FrameValidator.none, isNull, LinkOverride.default, timeoutMs)

  // copies of a response already waiting come within this of each other
  private val queuedMs = 100
  // at most this many copies are read, so a DUT that never stops can't hold up the test
  private val maxQueued = 20

  /**
   * The null unsolicited response the DUT sends as it starts, and the retries of it that queued up while it restarted,
   * which a manual restart can leave: the newest is confirmed, if it is, so that none left waiting looks like a retry
   * sent after the confirmation. Its sequence number is recorded.
   */
  private def startupNull(confirm: Boolean, record: Sequence => Unit = _ => ()): TestStep = new TestStep {
    def description: List[Comment] = List(
      "Read the null unsolicited response the DUT sends as it starts, and any retries of it already waiting",
      "Verify that each is empty, without error IIN bits",
      if (confirm) "Confirm the newest" else "Don't confirm it"
    )
    def run(driver: TestDriver): List[DeferredCheck] = {
      def read(timeoutMs: Option[Int]) =
        driver.readMessage(Unsolicited.Yes, FrameValidator.none, LinkOverride.default, timeoutMs)
      val first = read(None).getOrElse(throw new Exception("No unsolicited response received."))
      val queued = Iterator.continually(read(Some(queuedMs))).take(maxQueued).takeWhile(_.isDefined).flatten.toList
      if (queued.nonEmpty) driver.reporter.info(s"${queued.size} retries of it were already waiting")
      val all = first :: queued
      all.foreach { m =>
        validateUnsolicitedResponse(m)
        isNull.validate(driver.reporter, m)
      }
      record(all.last.ctrl.seq)
      if (confirm) driver.writeMessage(Requests.confirm(all.last.ctrl.seq, Unsolicited.Yes))
      Nil
    }
  }

  // the master's initialization of the DUT: a clear of IIN1.7, a poll of every input, and, last, an enable of
  // unsolicited responses
  private val initialization: TestStep =
    send(Requests.clearRestartIIN(Sequence.zero)) + expectSingleResponse(Sequence.zero, without(IIN.errors)) +
      send(Requests.readClass1230(Sequence.one)) + expectAtLeastOneResponse(Sequence.one, without(IIN.errors)) +
      send(Requests.allObjects(AppFunction.enableUnsolicited, Sequence.two, Group60Var2, Group60Var3, Group60Var4)) +
      expectSingleResponse(Sequence.two, isNull)

  // events the DUT reports unsolicited, which the harness confirms, and no more after them
  private def eventsAndNoMore(options: OptionSource): List[TestStep] = List(
    PluginSteps.generateUnsolicitedEvents(),
    // the DUT may hold events back for up to its hold-off time
    expectUnsolicitedResponse(
      AutoConfirm.Yes,
      FrameValidator.none,
      withEvents,
      LinkOverride.default,
      Some(
        options.get(UnsolicitedOptions.holdOffMs) + options.get(UnsolicitedOptions.unsolicitedConfirmTimeoutMs) +
          options.get(TestOptions.timingToleranceMs))
    ),
    expectNoUnsolicitedResponse(retryWindow(options))
  )

  // the UDP setup of the unsolicited tests: the DUT sends its null unsolicited response to the master's nullPort, and
  // every other UDP message to data
  private def unsolicited(options: OptionSource, listen: Int, nullPort: Int, data: UdpDestination): NetworkSetup =
    udp(options, listen, anyUdpMaster(options), data)
      .copy(unsolicited = Some(UdpUnsolicited(master(options), nullPort, data)))

  private val udpUnsolicited: List[OptionCondition] =
    List(SupportOptions.unsolicitedResponse, NetworkOptions.udpUnsolicited)

  /**
   * 9.5.3's Unsolicited Null 20000 and Unsolicited Null, Not 20000: the null, confirmed, and no more. They only set
   * where the null goes; the setup sends everything else as the DUT can: to port 20000, or to the port the null was
   * confirmed from, which also takes the null to port 20000 in the first test.
   */
  private def unsolicitedNull(gates: List[OptionCondition], nullPort: OptionSource => Int) =
    new Test {
      override protected def requirement: Requirement = Requirement.all(
        (udpUnsolicited ++ gates :+ Requirement.anyOf(
          OptionCondition(NetworkOptions.udpUnsolicitedTo20000),
          OptionCondition(NetworkOptions.udpUnsolicitedToOriginatingPort)))*)

      override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
        val to = nullPort(options)
        val data =
          if (options.get(NetworkOptions.udpUnsolicitedTo20000)) UdpDestination.Fixed(port)
          else UdpDestination.OriginatingPort
        List(
          (1 to 2) --> setUp(unsolicited(options, port, to, data)),
          3 --> udpFrom(dut(options), from(master(options), to)),
          4 --> PluginSteps.cyclePower,
          (5 to 6) --> startupNull(confirm = true),
          7 --> expectNoUnsolicitedResponse(retryWindow(options))
        )
      }
    }

  val unsolicitedNull20000: ProcedureDefinition = unsolicitedNull(Nil, _ => port)

  val unsolicitedNullOtherPort: ProcedureDefinition = unsolicitedNull(
    List(OptionCondition(NetworkOptions.udpUnsolicitedNullPort)),
    _.get(NetworkOptions.udpUnsolicitedPort))

  /** 9.5.3's Data Filled Messages to Port 20000 */
  val unsolicitedData20000: ProcedureDefinition = new Test {
    override protected def requiredOptions: List[OptionCondition] =
      udpUnsolicited :+ OptionCondition(NetworkOptions.udpUnsolicitedTo20000)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val List(generate, expect, noMore) = eventsAndNoMore(options)
      List(
        1 --> setUp(unsolicited(options, port, port, UdpDestination.Fixed(port))),
        (2 to 3) --> udpFrom(dut(options), from(master(options), port)),
        4 --> PluginSteps.cyclePower,
        (5 to 6) --> startupNull(confirm = true),
        (7 to 10) --> initialization,
        11 --> generate,
        (12 to 13) --> expect,
        14 --> noMore
      )
    }
  }

  /**
   * 9.5.3's Data Filled Messages to Originating Port: the null comes to port 20000, and the harness confirms it from
   * another port, where every later message must come
   */
  val unsolicitedDataOriginatingPort: ProcedureDefinition = new Test {
    override protected def requiredOptions: List[OptionCondition] =
      udpUnsolicited :+ OptionCondition(NetworkOptions.udpUnsolicitedToOriginatingPort)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val List(generate, expect, noMore) = eventsAndNoMore(options)
      var nullSeq: Option[Sequence] = None
      val other = UdpConnection(dut(options), from(master(options)))
      List(
        (1 to 2) --> setUp(unsolicited(options, port, port, UdpDestination.OriginatingPort)),
        (3 to 5) --> udpFrom(dut(options), from(master(options), port)),
        6 --> PluginSteps.cyclePower,
        7 --> startupNull(confirm = false, seq => nullSeq = Some(seq)),
        8 --> new TestStep {
          def description: List[Comment] = List(
            s"Confirm it from ${master(options)}, from a port the system chooses, other than 20000: from now on, the " +
              "harness sends from that port, and processes only the datagrams that come back to it")
          def run(driver: TestDriver): List[DeferredCheck] = {
            val bound = driver.connect(other)
            driver.reporter.info(s"Confirming from port ${bound.getPort}")
            driver.writeMessage(Requests.confirm(nullSeq.get, Unsolicited.Yes))
            Nil
          }
        },
        (9 to 12) --> initialization,
        13 --> generate,
        (14 to 15) --> expect,
        16 --> noMore
      )
    }
  }

  /** 9.5.3's Data Filled Messages to a Fixed Port Other Than 20000: the DUT and the master both use that port */
  val unsolicitedDataFixedPort: ProcedureDefinition = new Test {
    override protected def requiredOptions: List[OptionCondition] =
      udpUnsolicited :+ OptionCondition(NetworkOptions.udpFixedOtherPort)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val List(generate, expect, noMore) = eventsAndNoMore(options)
      val other = options.get(NetworkOptions.udpOtherPort)
      List(
        (1 to 3) --> setUp(unsolicited(options, other, other, UdpDestination.Fixed(other))),
        4 --> udpFrom(dut(options, other), from(master(options), other)),
        5 --> PluginSteps.cyclePower,
        (6 to 7) --> startupNull(confirm = true),
        (8 to 11) --> initialization,
        12 --> generate,
        (13 to 14) --> expect,
        15 --> noMore,
        16 --> describe("The cleanup restores the DUT's usual network setup")
      )
    }
  }

  /**
   * 9.5.4's test: unsolicited responses over UDP until a TCP connection is made, then over TCP only, while requests over
   * UDP are still answered over UDP
   */
  val unsolicitedUdpAndTcp: ProcedureDefinition = new Test {
    override protected def requiredOptions: List[OptionCondition] = udpUnsolicited ++ List(
      OptionCondition(NetworkOptions.udpUnsolicitedTo20000),
      OptionCondition(NetworkOptions.udpAndTcp),
      OptionCondition(NetworkOptions.tcp))

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val udpLink = UdpConnection(dut(options), from(master(options), port))
      val tcpLink = TcpConnection(dut(options), from(master(options)))
      val allowed =
        if (options.get(NetworkOptions.tcpAnyAddress) && options.get(NetworkOptions.udpAnyAddress)) AllowedMasters.Any
        else AllowedMasters.Address(master(options))
      val setup = NetworkSetup(
        NetworkMode.Both,
        port,
        allowed,
        UdpDestination.Fixed(port),
        Some(UdpUnsolicited(master(options), port, UdpDestination.Fixed(port))))
      val noUdp = new TestStep {
        def description: List[Comment] =
          List("Verify that no UDP datagram has come from the DUT since the TCP connection was made")
        def run(driver: TestDriver): List[DeferredCheck] = {
          val count = driver.drainDatagrams(udpLink)
          if (count > 0) throw new Exception(s"The DUT sent $count UDP datagram(s) after the TCP connection was made")
          Nil
        }
      }
      val List(generate, expect, _) = eventsAndNoMore(options)
      List(
        1 --> setUp(setup),
        2 --> udpFrom(dut(options), from(master(options), port)),
        3 --> PluginSteps.cyclePower,
        4 --> startupNull(confirm = false),
        5 --> describe("Don't confirm it"),
        6 --> expectNull(AutoConfirm.No, retryWindow(options)),
        7 --> describe("Don't confirm it"),
        (8 to 9) --> new TestStep {
          def description: List[Comment] = List(
            s"Issue a TCP connection to the DUT's port $port from ${master(options)} at once, keeping the UDP socket " +
              "open alongside it",
            "Verify that the DUT accepts it"
          )
          def run(driver: TestDriver): List[DeferredCheck] = {
            val earlier = driver.drainDatagrams(udpLink)
            if (earlier > 0) driver.reporter.info(s"$earlier more UDP datagram(s) came before the TCP connection")
            val connected =
              try driver.connect(tcpLink, keep = true)
              catch {
                case ex @ (_: ConnectException | _: SocketTimeoutException) =>
                  throw new Exception(s"The DUT didn't accept the connection: ${ex.getMessage}", ex)
              }
            // what came while the connection was being made may have been sent before the DUT saw it: not counted
            val meanwhile = driver.drainDatagrams(udpLink)
            if (meanwhile > 0)
              driver.reporter.info(s"$meanwhile UDP datagram(s) came while the TCP connection was made")
            driver.reporter.info(s"Connected from port ${connected.getPort}")
            Nil
          }
        },
        (10 to 12) --> expectNull(AutoConfirm.No, retryWindow(options)),
        13 --> noUdp,
        (14 to 15) --> expectNull(AutoConfirm.Yes, retryWindow(options)),
        (16 to 19) --> initialization,
        20 --> generate,
        (21 to 22) --> (expect + noUdp),
        23 --> new TestStep {
          def description: List[Comment] = List(
            s"Send a class 1, 2, 3 and 0 read over UDP to the DUT's port $port, from port $port, keeping the TCP " +
              "connection open")
          def run(driver: TestDriver): List[DeferredCheck] = {
            driver.connect(udpLink, keep = true)
            driver.writeMessage(Requests.readClass1230(Sequence(3)))
            Nil
          }
        },
        24 --> expectAtLeastOneResponse(Sequence(3))
      )
    }
  }
}
