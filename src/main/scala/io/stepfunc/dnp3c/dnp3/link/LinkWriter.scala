// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.link

import io.stepfunc.dnp3c.dnp3.TestReporter
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE

object LinkWriter {

  def write(
      src: UInt16LE,
      dest: UInt16LE,
      block: Option[Byte],
      reporter: TestReporter)(data: List[Byte])(fun: LinkFrame => Unit): Unit = {
    val control =
      block.map(Control.apply).getOrElse(Control(true, false, false, LinkFunction.UNCONFIRMED_USER_DATA))
    val frame = LinkFrame.withData(control, dest, src, data)
    reporter.transmit(frame)
    fun(frame)
  }

}
