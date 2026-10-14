// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol.channel

import java.nio.ByteBuffer

import com.fazecast.jSerialComm.SerialPort

/** How a serial port is set up: the port's name (e.g. /dev/ttyUSB0 or COM3), and its character format */
final case class SerialSettings(port: String, baudRate: Int, dataBits: Int, parity: String, stopBits: Int) {

  // the bits on the line for each byte: a start bit, the data bits, a parity bit if any, and the stop bits
  def bitsPerByte: Int = 1 + dataBits + (if (parity.equalsIgnoreCase("none")) 0 else 1) + stopBits

  // how long the bytes take on the line, in milliseconds
  def transmitMs(bytes: Int): Double = bytes * bitsPerByte * 1000.0 / baudRate

  def describe: String = s"$port, $baudRate baud, $dataBits data bits, parity $parity, $stopBits stop bit(s)"
}

/** Talks to the DUT over a serial port */
final class SerialChannel private (port: SerialPort) extends ReadableWritableChannel {

  private def name: String = port.getSystemPortName

  // a failure of the port, e.g. an adapter unplugged, is the bench's problem
  private def failed(what: String): Nothing =
    throw new BenchException(s"$what serial port $name failed (error ${port.getLastErrorCode})", null)

  // writes everything, failing if the port takes nothing for writeStallMs: jSerialComm can report an error as 0
  override def write(buffer: ByteBuffer): Unit = {
    val bytes = new Array[Byte](buffer.remaining())
    buffer.get(bytes)
    var written = 0
    var progressAt = System.nanoTime()
    while (written < bytes.length) {
      val n = port.writeBytes(bytes, bytes.length - written, written)
      if (n < 0) failed("Writing to")
      if (n > 0) progressAt = System.nanoTime()
      else if (System.nanoTime() - progressAt > SerialChannel.writeStallMs * 1000000L) failed("Writing to")
      else Thread.sleep(1)
      written += n
    }
  }

  // what arrives within the timeout; 0 if nothing does. jSerialComm rounds a timeout to tenths of a second, and on
  // Linux wraps one over 25.5 s, so the wait is made of bounded slices until the deadline.
  override def read(buffer: ByteBuffer, timeoutMs: Int): Int = {
    val deadline = System.nanoTime() + timeoutMs * 1000000L
    val bytes = new Array[Byte](buffer.remaining())
    var n = 0
    var left = timeoutMs.toLong
    while (n == 0 && left > 0) {
      val slice = math.min(math.max(left, SerialChannel.minSliceMs), SerialChannel.maxSliceMs).toInt
      port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING | SerialPort.TIMEOUT_WRITE_BLOCKING, slice, 0)
      n = port.readBytes(bytes, bytes.length)
      if (n < 0) failed("Reading from")
      left = (deadline - System.nanoTime()) / 1000000L
    }
    buffer.put(bytes, 0, n)
    n
  }

  // asserts or clears RTS, which the DUT may see as carrier (DCD)
  def setRts(asserted: Boolean): Unit =
    if (!(if (asserted) port.setRTS() else port.clearRTS())) failed("Setting RTS on")

  override def close(): Unit = port.closePort()
}

object SerialChannel {

  // a read waits in slices of at least a tenth of a second, the timeout's resolution, and at most a second
  private val minSliceMs = 100L
  private val maxSliceMs = 1000L

  // how long a write may make no progress
  private val writeStallMs = 5000L

  /** opens the port; one the bench can't open is a BenchException */
  def open(settings: SerialSettings): SerialChannel = {
    if (settings.port.isEmpty) throw new BenchException("No serial port is set (serial.port)", null)
    val port =
      try SerialPort.getCommPort(settings.port)
      catch {
        case ex: Exception => throw new BenchException(s"No serial port ${settings.port}: ${ex.getMessage}", ex)
      }
    val parity = settings.parity.toLowerCase match {
      case "none" => SerialPort.NO_PARITY
      case "even" => SerialPort.EVEN_PARITY
      case "odd" => SerialPort.ODD_PARITY
      case "mark" => SerialPort.MARK_PARITY
      case "space" => SerialPort.SPACE_PARITY
      case other => throw new BenchException(s"Unknown parity $other", null)
    }
    val stopBits = if (settings.stopBits == 2) SerialPort.TWO_STOP_BITS else SerialPort.ONE_STOP_BIT
    port.setComPortParameters(settings.baudRate, settings.dataBits, stopBits, parity)
    port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)
    if (!port.openPort())
      throw new BenchException(
        s"Can't open serial port ${settings.port} (error ${port.getLastErrorCode}): is it there, and free?",
        null)
    new SerialChannel(port)
  }
}

/** Opens the serial port for each connection */
final class SerialChannelFactory(settings: SerialSettings) extends ChannelFactory {
  def open(): SerialChannel = SerialChannel.open(settings)
}
