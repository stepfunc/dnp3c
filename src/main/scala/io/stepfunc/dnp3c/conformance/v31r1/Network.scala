// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import java.net.{ ConnectException, InetSocketAddress, SocketTimeoutException }

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ ChannelOptions, NetworkOptions }
import io.stepfunc.dnp3c.conformance.s6.{ expectFrame, sendLinkControl, withAnyControl }
import io.stepfunc.dnp3c.dnp3._

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
    val wildcard = running.contains(tcpWildcard)
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
        ConfigProblem(NetworkOptions.tcpOtherPort.id, port.toString, "must be a port other than 20000"))
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
}
