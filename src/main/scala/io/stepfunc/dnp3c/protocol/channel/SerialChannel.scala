// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol.channel

import java.nio.ByteBuffer
import java.util.concurrent.{ LinkedBlockingQueue, TimeUnit }

import com.fazecast.jSerialComm.SerialPort

/** How a serial port is set up: the port's name (e.g. /dev/ttyUSB0 or COM3), and its character format */
final case class SerialSettings(port: String, baudRate: Int, dataBits: Int, parity: String, stopBits: Int) {

  // the bits on the line for each byte: a start bit, the data bits, a parity bit if any, and the stop bits
  def bitsPerByte: Int = 1 + dataBits + (if (parity.equalsIgnoreCase("none")) 0 else 1) + stopBits

  // how long the bytes take on the line, in milliseconds
  def transmitMs(bytes: Int): Double = bytes * bitsPerByte * 1000.0 / baudRate

  def describe: String = s"$port, $baudRate baud, $dataBits data bits, parity $parity, $stopBits stop bit(s)"
}

/**
 * Talks to the DUT over a serial port. A thread reads the port all the time, so that each byte's arrival is timed when
 * it comes, whatever the harness is doing, e.g. writing; reads take what it has read.
 */
final class SerialChannel private (port: SerialPort) extends ReadableWritableChannel {

  private def name: String = port.getSystemPortName

  // a failure of the port, e.g. an adapter unplugged, is the bench's problem
  private def failed(what: String): Nothing =
    throw new BenchException(s"$what serial port $name failed (error ${port.getLastErrorCode})", null)

  // what the reader has read, with when it arrived, by System.nanoTime
  private val chunks = new LinkedBlockingQueue[(Array[Byte], Long)]()
  // the rest of a chunk that a read's buffer had no room for
  private var leftover: Option[(Array[Byte], Long)] = None
  @volatile private var running = true
  @volatile private var readFailed = false
  private var arrival: Option[Long] = None

  private val reader = new Thread(
    () => {
      val bytes = new Array[Byte](4096)
      while (running) {
        val n = port.readBytes(bytes, bytes.length)
        if (n < 0) {
          if (running) readFailed = true
          running = false
        } else if (n > 0) chunks.put((bytes.take(n), System.nanoTime()))
      }
    },
    s"dnp3c serial reader ($name)"
  )
  reader.setDaemon(true)
  reader.start()

  // writes everything, returning once it's sent; fails if the port takes nothing for writeStallMs, as jSerialComm can
  // report an error as 0
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

  // what arrives within the timeout; 0 if nothing does
  override def read(buffer: ByteBuffer, timeoutMs: Int): Int = {
    val next = leftover.orElse(Option(chunks.poll(math.max(timeoutMs, 0).toLong, TimeUnit.MILLISECONDS)))
    leftover = None
    next match {
      case None =>
        if (readFailed) failed("Reading from")
        0
      case Some((bytes, at)) =>
        val n = math.min(bytes.length, buffer.remaining())
        buffer.put(bytes, 0, n)
        if (n < bytes.length) leftover = Some((bytes.drop(n), at))
        arrival = Some(at)
        n
    }
  }

  override def lastReadAt: Option[Long] = arrival

  // asserts or clears RTS, which the DUT may see as carrier (DCD)
  def setRts(asserted: Boolean): Unit =
    if (!(if (asserted) port.setRTS() else port.clearRTS())) failed("Setting RTS on")

  override def close(): Unit = {
    running = false
    port.closePort()
    reader.join(1000)
  }
}

object SerialChannel {

  // how long the reader waits on the port at a time, before it checks whether the channel has closed
  private val readSliceMs = 100

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
    // reads wait a slice at most; writes block until the bytes are sent
    port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING | SerialPort.TIMEOUT_WRITE_BLOCKING, readSliceMs, 0)
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
