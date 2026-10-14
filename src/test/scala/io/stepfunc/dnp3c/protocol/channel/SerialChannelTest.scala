// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol.channel

import java.io.{ BufferedReader, InputStreamReader }
import java.nio.ByteBuffer
import java.util.concurrent.{ CompletableFuture, TimeUnit }

import scala.util.Try

import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// the serial channel over a pair of pseudo-terminals joined by a Python script, where the host has them
@RunWith(classOf[JUnitRunner])
class SerialChannelTest extends AnyFunSuite with Matchers {

  // copies whatever arrives at either pty to the other, and prints the two ports' names
  private val bridge =
    """import os, pty, select, sys
      |a, sa = pty.openpty()
      |b, sb = pty.openpty()
      |print(os.ttyname(sa)); print(os.ttyname(sb)); sys.stdout.flush()
      |while True:
      |    r, _, _ = select.select([a, b], [], [])
      |    for fd in r:
      |        data = os.read(fd, 4096)
      |        os.write(b if fd == a else a, data)
      |""".stripMargin

  private def withPair(test: (SerialChannel, SerialChannel) => Unit): Unit = {
    val process = Try(new ProcessBuilder("python3", "-c", bridge).start()).toOption
    assume(process.isDefined, "python3 isn't available")
    val p = process.get
    try {
      // the names, read in the background so that a script that never prints them can't hang the test
      val names = CompletableFuture.supplyAsync { () =>
        val reader = new BufferedReader(new InputStreamReader(p.getInputStream))
        List(reader.readLine(), reader.readLine())
      }
      val ports = Try(names.get(10, TimeUnit.SECONDS)).getOrElse(Nil)
      assume(ports.size == 2 && ports.forall(n => n != null && n.startsWith("/dev/")), "no pseudo-terminals")
      def open(name: String) = SerialChannel.open(SerialSettings(name, 9600, 8, "none", 1))
      val a = open(ports(0))
      try {
        val b = open(ports(1))
        try test(a, b)
        finally b.close()
      } finally a.close()
    } finally {
      p.destroy()
      if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly()
    }
  }

  private def readAll(channel: SerialChannel, count: Int): List[Byte] = {
    val buffer = ByteBuffer.allocate(4096)
    val deadline = System.currentTimeMillis() + 5000
    while (buffer.position() < count && System.currentTimeMillis() < deadline) channel.read(buffer, 100)
    buffer.flip()
    val bytes = new Array[Byte](buffer.remaining())
    buffer.get(bytes)
    bytes.toList
  }

  test("what one end writes, the other reads") {
    withPair { (a, b) =>
      val bytes = (0 until 300).map(_.toByte).toList
      a.write(ByteBuffer.wrap(bytes.toArray))
      readAll(b, bytes.size) should equal(bytes)
    }
  }

  test("bytes are timed when they arrive, not when they're read") {
    withPair { (a, b) =>
      val sent = System.nanoTime()
      b.write(ByteBuffer.wrap(Array[Byte](1, 2, 3)))
      Thread.sleep(1000)
      val reading = System.nanoTime()
      a.read(ByteBuffer.allocate(16), 1000) should be > 0
      val arrived = a.lastReadAt.get
      arrived should be >= sent
      // well before the read, a second after: the bridge and the reader have most of a second to pass them on
      (reading - arrived) / 1000000 should be >= 200L
    }
  }

  test("a read that gets nothing within its timeout returns nothing, once the whole timeout has passed") {
    withPair { (a, _) =>
      for (timeoutMs <- List(250, 1500)) {
        val start = System.currentTimeMillis()
        a.read(ByteBuffer.allocate(16), timeoutMs) should equal(0)
        System.currentTimeMillis() - start should be >= timeoutMs.toLong
      }
      a.read(ByteBuffer.allocate(16), 0) should equal(0)
    }
  }

  test("a port that isn't there, or isn't set, is the bench's problem") {
    a[BenchException] should be thrownBy SerialChannel.open(SerialSettings("/dev/no-such-port", 9600, 8, "none", 1))
    a[BenchException] should be thrownBy SerialChannel.open(SerialSettings("", 9600, 8, "none", 1))
  }

  test("a character takes a start bit, its data bits, a parity bit if any, and its stop bits") {
    SerialSettings("x", 9600, 8, "none", 1).bitsPerByte should equal(10)
    SerialSettings("x", 9600, 8, "even", 2).bitsPerByte should equal(12)
    SerialSettings("x", 9600, 8, "none", 1).transmitMs(96) should equal(100.0)
  }
}
