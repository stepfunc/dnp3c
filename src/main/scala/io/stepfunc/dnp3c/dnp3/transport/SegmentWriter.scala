// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.transport

import io.stepfunc.dnp3c.dnp3.TestReporter

import scala.annotation.tailrec

object SegmentWriter {

  // Chuncks up an arbitrary sized data into segments, forwarding them to a receiving function
  def write(bytes: List[Byte], seq: Byte, reporter: TestReporter)(fun: List[Byte] => Unit): Unit = {

    val iterator = bytes.grouped(Transport.maxPayload)

    @tailrec
    def recurse(first: Boolean, seq: Byte): Unit = {
      val bytes = iterator.next()
      val fin = !iterator.hasNext
      val header = Transport.Header(first, fin, seq)
      reporter.transmit(header)
      fun(header.toByte :: bytes)
      if (!fin) recurse(false, Transport.next(seq))
    }

    if (iterator.hasNext) recurse(true, seq)
  }

}
