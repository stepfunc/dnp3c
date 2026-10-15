// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application.pcap

import java.io.{ Closeable, IOException }
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }

import io.stepfunc.dnp3c.conformance.api.ErrorText

import org.pcap4j.core.BpfProgram.BpfCompileMode
import org.pcap4j.core.PcapHandle.BlockingMode
import org.pcap4j.core.PcapNetworkInterface.PromiscuousMode
import org.pcap4j.core.{ PcapAddress, PcapNetworkInterface, Pcaps }

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

trait PacketCapture {
  def open(path: String): Closeable
}

object PacketCapture {

  private val lingerMs = 200
  private val pollMs = 5L

  private def matches(address: String, addresses: List[PcapAddress]): Boolean = {
    addresses.exists(x => x.getAddress.toString == address)
  }

  // every capture starts here, by loading libpcap: report a failure to load it as an error callers expect, not the
  // LinkageError they'd let through
  def interfaces: List[PcapNetworkInterface] =
    try Pcaps.findAllDevs().asScala.toList
    catch { case ex: LinkageError => throw new Exception(s"Couldn't load libpcap: $ex", ex) }

  // captures the packets a BPF filter selects
  def create(nic: String, filter: String): PacketCapture = new PacketCapture {

    val interface: PcapNetworkInterface = {
      interfaces.find(d => matches(nic, d.getAddresses.asScala.toList)).getOrElse {
        throw new Exception("Unable to find network adapter: " + nic)
      }
    }

    override def open(path: String): Closeable = {

      val handle = interface.openLive(65535, PromiscuousMode.PROMISCUOUS, 10)
      handle.setFilter(filter, BpfCompileMode.OPTIMIZE)
      // polled, not a blocking loop: before libpcap 1.10, breaking a blocking loop on Linux waits for the next packet,
      // and once the procedure's connection has closed none comes
      handle.setBlockingMode(BlockingMode.NONBLOCKING)
      val dumper = handle.dumpOpen(path)

      // write each packet as it arrives, with the time it was captured: read only when the capture closed, the
      // kernel's buffer filled (dropping the rest) and each packet got the time it was written
      def writeAvailable(): Boolean = {
        val packet = handle.getNextRawPacket
        if (packet != null) dumper.dumpRaw(packet, handle.getTimestamp)
        packet != null
      }

      val running = new AtomicBoolean(true)
      val failure = new AtomicReference[Option[Throwable]](None)
      val writer = new Thread(
        () =>
          try {
            while (running.get) {
              if (!writeAvailable()) Thread.sleep(pollMs)
            }
          } catch {
            case _: InterruptedException =>
            case NonFatal(ex) => failure.set(Some(ex))
          },
        "packet capture"
      )
      writer.setDaemon(true)
      writer.start()

      () => {
        // the last packets, e.g. the connection closing, are still on their way
        var interrupted =
          try {
            Thread.sleep(lingerMs)
            false
          } catch { case _: InterruptedException => true }
        // the writer uses the dumper and handle: it must stop before they're closed, even if this thread is interrupted
        running.set(false)
        while (writer.isAlive) {
          try writer.join()
          catch { case _: InterruptedException => interrupted = true }
        }
        try {
          if (failure.get.isEmpty) while (writeAvailable()) {}
        } finally {
          dumper.close()
          handle.close()
        }
        if (interrupted) Thread.currentThread().interrupt()
        failure.get.foreach(ex => throw new IOException(s"Packet capture failed: ${ErrorText.of(ex)}", ex))
      }
    }

  }
}
