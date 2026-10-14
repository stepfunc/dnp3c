// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3

import java.net.{ ConnectException, InetAddress, InetSocketAddress, ServerSocket, Socket }
import java.util.concurrent.{ CompletableFuture, TimeUnit }

import io.stepfunc.dnp3c.dnp3.link.{ Control, LinkFrame }
import io.stepfunc.dnp3c.protocol.channel.{ LocalBindException, TcpClientChannelFactory }
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// the driver's own connections (3.1r1's section 9), to servers on the loopback address
@RunWith(classOf[JUnitRunner])
class NetworkDriverTest extends AnyFunSuite with Matchers {

  private val loopback = InetAddress.getLoopbackAddress
  private val anyPort = new InetSocketAddress(loopback, 0)

  private def driver() = new DefaultTestDriver(
    StubDriver.reporter(),
    new TcpClientChannelFactory(anyPort),
    UInt16LE(1),
    UInt16LE(1024),
    200,
    1000,
    1000)

  // a server that handles one connection in the background; what the handler returns completes the future
  private def serve[A](handle: Socket => A): (InetSocketAddress, CompletableFuture[A]) = {
    val server = new ServerSocket(0, 1, loopback)
    val result = CompletableFuture.supplyAsync[A] { () =>
      try {
        val socket = server.accept()
        try handle(socket)
        finally socket.close()
      } finally server.close()
    }
    (new InetSocketAddress(loopback, server.getLocalPort), result)
  }

  // a port nothing listens on
  private def closedPort(): InetSocketAddress = {
    val server = new ServerSocket(0, 1, loopback)
    server.close()
    new InetSocketAddress(loopback, server.getLocalPort)
  }

  // reads until the harness closes the connection
  private def drain(s: Socket): Unit = while (s.getInputStream.read() >= 0) {}

  private def waitFor[A](f: CompletableFuture[A]): A = f.get(5, TimeUnit.SECONDS)

  test("connects from the local address given, and says from which port") {
    val (address, accepted) = serve(_.getPort)
    val local = driver().connect(TcpConnection(address, anyPort))
    local.getPort should not equal 0
    waitFor(accepted) should equal(local.getPort)
  }

  test("a refused connection throws") {
    a[ConnectException] should be thrownBy driver().connect(TcpConnection(closedPort(), anyPort))
  }

  test("a local address that isn't this host's is the bench's problem") {
    val elsewhere = new InetSocketAddress("192.0.2.1", 0)
    a[LocalBindException] should be thrownBy driver().connect(TcpConnection(closedPort(), elsewhere))
    a[LocalBindException] should be thrownBy driver().probe(TcpConnection(closedPort(), elsewhere))
  }

  test("a probe of a port nothing listens on is refused") {
    driver().probe(TcpConnection(closedPort(), anyPort)) should equal(ProbeResult.Refused)
  }

  test("a probe the DUT accepts, then closes, is closed") {
    val (address, done) = serve(_ => ())
    driver().probe(TcpConnection(address, anyPort)) should equal(ProbeResult.Closed)
    waitFor(done)
  }

  test("a probe the DUT keeps open without answering is silent") {
    val (address, done) = serve(drain)
    driver().probe(TcpConnection(address, anyPort)) should equal(ProbeResult.Silent)
    waitFor(done)
  }

  test("a probe the DUT answers is answered") {
    val (address, done) = serve { s =>
      s.getInputStream.read(new Array[Byte](10))
      s.getOutputStream.write(LinkFrame.headerOnly(Control(0x0B.toByte), UInt16LE(1), UInt16LE(1024)).toBytes.toArray)
      drain(s)
    }
    driver().probe(TcpConnection(address, anyPort)) should equal(ProbeResult.Answered)
    waitFor(done)
  }
}
