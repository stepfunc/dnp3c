// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import java.net.{ ConnectException, InetAddress, InetSocketAddress, SocketTimeoutException }

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ ChannelOptions, NetworkOptions }
import io.stepfunc.dnp3c.conformance.s6.{ expectFrame, sendLinkControl, withAnyControl }
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps.{ expectAtLeastOneResponse, send }
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.{ IIN, Message, Sequence }
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

  /**
   * What the selected network tests that will run need configured: an address of this host to connect from, and those
   * of the test that uses them
   */
  def configurationProblems(options: OptionSource, selected: Iterable[TestProcedure]): List[ConfigProblem] = {
    val running = selected.map(_.definition).filter(_.disabledBy(options).isEmpty).toSet
    def address(option: StringOption, why: String): Option[ConfigProblem] = {
      val value = options.get(option)
      Option.when(!isAddress(value))(ConfigProblem(option.id, value, s"needs an IP address of this host, $why"))
    }
    val any = running.exists(_.isInstanceOf[Test])
    val wildcard = running.contains(tcpWildcard) || running.contains(udpWildcard)
    val group = options.get(NetworkOptions.multicastGroup)
    List(
      Option.when(any)(address(NetworkOptions.masterAddress, "which 3.1r1's network tests connect from")).flatten,
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
      Option.when(running.contains(tcpOtherPort) && options.get(NetworkOptions.tcpOtherPort) == port)(
        ConfigProblem(NetworkOptions.tcpOtherPort.id, port.toString, "must be a port other than 20000")),
      Option.when(running.contains(udpOtherPort) && options.get(NetworkOptions.udpOtherPort) == port)(
        ConfigProblem(NetworkOptions.udpOtherPort.id, port.toString, "must be a port other than 20000")),
      Option.when(running.contains(udpMulticast) && !isMulticast(group))(
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

  private val ipv4 = """(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})""".r

  // an IPv4 or IPv6 address, other than the one for every address
  private def isAddress(s: String): Boolean = s match {
    case ipv4(a, b, c, d) => List(a, b, c, d).forall(_.toInt <= 255) && s != "0.0.0.0"
    case _ =>
      s.contains(':') && s.forall(c => Character.digit(c, 16) >= 0 || c == ':' || c == '.') &&
      s.exists(c => c != ':' && c != '0')
  }

  private def isMulticast(s: String): Boolean =
    isAddress(s) && scala.util.Try(InetAddress.getByName(s).isMulticastAddress).getOrElse(false)

  /** a section 9 test: restores the DUT's network once it ends */
  abstract class Test extends ProcedureDefinition {
    override protected def cleanup(options: OptionSource): Iterable[LabeledStep] =
      List(Label("cleanup") --> restore)
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

  private def setUp(setup: NetworkSetup): TestStep = new TestStep {
    def description: List[Comment] = List(s"Set up the DUT's network to ${setup.describe}")
    def run(driver: TestDriver): List[DeferredCheck] = {
      driver.close()
      driver.reporter.plugin.configureNetwork(driver.reporter.pluginReporter(), setup)
      Nil
    }
  }

  private val restore: TestStep = new TestStep {
    def description: List[Comment] = List("Restore the DUT's usual network setup")
    def run(driver: TestDriver): List[DeferredCheck] = {
      driver.close()
      driver.reporter.plugin.restoreNetwork(driver.reporter.pluginReporter())
      Nil
    }
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
  private val noLinkStatus: TestStep = sendLinkControl(0xC9.toByte) + new TestStep {
    def description: List[Comment] = List("Verify that the DUT doesn't answer within the link timeout")
    def run(driver: TestDriver): List[DeferredCheck] = {
      driver.readFrame().foreach(f => throw new Exception(s"The DUT answered: $f"))
      Nil
    }
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

  private val closeGracefully: TestStep = new TestStep {
    def description: List[Comment] = List("Close the connection gracefully")
    def run(driver: TestDriver): List[DeferredCheck] = {
      driver.close()
      Nil
    }
  }

  private val disconnectNoticed: TestStep = new TestStep {
    def description: List[Comment] = List("Verify that the DUT noticed the connection close")
    def run(driver: TestDriver): List[DeferredCheck] =
      driver.reporter.plugin.verifyDisconnectNoticed(driver.reporter.checkReporter(CheckTiming.Deferrable)).toList
  }

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
}
