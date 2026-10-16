// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3

import java.net.{
  ConnectException, DatagramPacket, DatagramSocket, InetAddress, InetSocketAddress, ServerSocket, Socket
}
import java.util.concurrent.{ CompletableFuture, TimeUnit }

import io.stepfunc.dnp3c.dnp3.link.{ Control, LinkFrame, LinkFrameParser }
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
    1000,
    500)

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

  // a UDP server on the loopback address that handles what it receives in the background
  private def serveUdp[A](handle: DatagramSocket => A): (InetSocketAddress, CompletableFuture[A]) = {
    val socket = new DatagramSocket(new InetSocketAddress(loopback, 0))
    socket.setSoTimeout(5000)
    val result = CompletableFuture.supplyAsync[A] { () =>
      try handle(socket)
      finally socket.close()
    }
    (new InetSocketAddress(loopback, socket.getLocalPort), result)
  }

  private def receive(socket: DatagramSocket): DatagramPacket = {
    val packet = new DatagramPacket(new Array[Byte](65535), 65535)
    socket.receive(packet)
    packet
  }

  private val linkStatus = LinkFrame.headerOnly(Control(0x0B.toByte), UInt16LE(1), UInt16LE(1024)).toBytes.toArray

  test("over UDP, the DUT's datagram back to the port the harness sends from is read") {
    val (address, done) = serveUdp { s =>
      val request = receive(s)
      s.send(new DatagramPacket(linkStatus, linkStatus.length, request.getSocketAddress))
      request.getPort
    }
    val dut = driver()
    val local = dut.connect(UdpConnection(address, anyPort))
    dut.writeFrame(LinkFrame.headerOnly(Control(0xC9.toByte), UInt16LE(1024), UInt16LE(1)))
    dut.readFrame().map(_.header.ctrl.toByte) should equal(Some(0x0B.toByte))
    waitFor(done) should equal(local.getPort)
  }

  test("over UDP, a link frame that doesn't end in its datagram fails") {
    val (address, done) = serveUdp { s =>
      val request = receive(s)
      s.send(new DatagramPacket(linkStatus, 6, request.getSocketAddress))
      s.send(new DatagramPacket(linkStatus.drop(6), linkStatus.length - 6, request.getSocketAddress))
    }
    val dut = driver()
    dut.connect(UdpConnection(address, anyPort))
    dut.writeFrame(LinkFrame.headerOnly(Control(0xC9.toByte), UInt16LE(1024), UInt16LE(1)))
    an[Exception] should be thrownBy dut.readFrame()
    waitFor(done)
  }

  test("over UDP, every frame of a datagram is read, and a broken one at its end fails before any is") {
    val (address, done) = serveUdp { s =>
      val request = receive(s)
      val two = linkStatus ++ linkStatus
      s.send(new DatagramPacket(two, two.length, request.getSocketAddress))
      val broken = linkStatus :+ 0x05.toByte
      s.send(new DatagramPacket(broken, broken.length, request.getSocketAddress))
    }
    val dut = driver()
    dut.connect(UdpConnection(address, anyPort))
    dut.writeFrame(LinkFrame.headerOnly(Control(0xC9.toByte), UInt16LE(1024), UInt16LE(1)))
    dut.readFrame() should not be empty
    dut.readFrame() should not be empty
    an[Exception] should be thrownBy dut.readFrame()
    waitFor(done)
  }

  test("over UDP, an empty datagram is ignored, and the frame after it read") {
    val (address, done) = serveUdp { s =>
      val request = receive(s)
      s.send(new DatagramPacket(new Array[Byte](0), 0, request.getSocketAddress))
      s.send(new DatagramPacket(linkStatus, linkStatus.length, request.getSocketAddress))
    }
    val dut = driver()
    dut.connect(UdpConnection(address, anyPort))
    dut.writeFrame(LinkFrame.headerOnly(Control(0xC9.toByte), UInt16LE(1024), UInt16LE(1)))
    dut.readFrame() should not be empty
    waitFor(done)
  }

  test("over UDP, a reconnection after a close sends from the same port") {
    val (address, done) = serveUdp(s => (receive(s).getPort, receive(s).getPort))
    val dut = driver()
    val local = dut.connect(UdpConnection(address, anyPort))
    val request = LinkFrame.headerOnly(Control(0xC9.toByte), UInt16LE(1024), UInt16LE(1))
    dut.writeFrame(request)
    dut.close()
    dut.writeFrame(request)
    waitFor(done) should equal((local.getPort, local.getPort))
  }

  test("over UDP, a close leaves the socket bound, so what the DUT sends meanwhile is read") {
    val (address, done) = serveUdp { s =>
      val request = receive(s)
      s.send(new DatagramPacket(linkStatus, linkStatus.length, request.getSocketAddress))
    }
    val dut = driver()
    dut.connect(UdpConnection(address, anyPort))
    dut.writeFrame(LinkFrame.headerOnly(Control(0xC9.toByte), UInt16LE(1024), UInt16LE(1)))
    waitFor(done)
    dut.close()
    dut.readFrame() should not be empty
    dut.shutdown()
  }

  test("a connection kept open while another is current is current again later, its datagrams counted meanwhile") {
    val (udpAddress, udpDone) = serveUdp { s =>
      val request = receive(s)
      for (_ <- 1 to 2) s.send(new DatagramPacket(linkStatus, linkStatus.length, request.getSocketAddress))
      receive(s).getPort
    }
    val (tcpAddress, tcpDone) = serve(drain)
    val dut = driver()
    val udp = UdpConnection(udpAddress, anyPort)
    val local = dut.connect(udp)
    dut.writeFrame(LinkFrame.headerOnly(Control(0xC9.toByte), UInt16LE(1024), UInt16LE(1)))
    dut.connect(TcpConnection(tcpAddress, anyPort), keep = true)
    // both datagrams arrive while TCP is current
    Thread.sleep(200)
    dut.drainDatagrams(udp) should equal(2)
    dut.drainDatagrams(udp) should equal(0)
    dut.connect(udp, keep = true) should equal(local)
    dut.writeFrame(LinkFrame.headerOnly(Control(0xC9.toByte), UInt16LE(1024), UInt16LE(1)))
    waitFor(udpDone) should equal(local.getPort)
    dut.shutdown()
    waitFor(tcpDone)
  }

  test("datagrams sent alongside the connection go one frame each, to the destination given") {
    val (address, done) = serveUdp(s => (receive(s), receive(s)))
    // a read of 90 headers, 272 bytes, takes two frames
    val message = Requests.readAllObjects(app.Sequence.one, List.fill(90)(app.objects.Group60Var1)*)
    driver().sendDatagrams(address, anyPort, message, LinkOverride.destination(UInt16LE(0xFFFF)))
    val (first, second) = waitFor(done)
    for (p <- List(first, second)) {
      val bytes = p.getData.take(p.getLength)
      bytes.take(2).toList should equal(List(0x05.toByte, 0x64.toByte))
      ((bytes(4) & 0xFF) | ((bytes(5) & 0xFF) << 8)) should equal(0xFFFF)
      bytes.length should equal(10 + LinkFrameParser.calcTotalSize((bytes(2) & 0xFF) - 5))
    }
  }
}
