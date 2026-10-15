// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol.channel

import java.net.{ InetAddress, InetSocketAddress, ServerSocket, StandardSocketOptions }
import java.nio.channels.SocketChannel

import scala.util.Using

import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class TcpClientChannelFactoryTest extends AnyFunSuite with Matchers {

  test("a connection has Nagle's algorithm off, so each write goes out at once") {
    Using.resource(new ServerSocket(0, 1, InetAddress.getLoopbackAddress)) { server =>
      Using.resource(SocketChannel.open()) { socket =>
        TcpClientChannelFactory.connect(socket, server.getLocalSocketAddress, None, 1000)
        socket.getOption(StandardSocketOptions.TCP_NODELAY) should equal(true)
      }
    }
  }
}
