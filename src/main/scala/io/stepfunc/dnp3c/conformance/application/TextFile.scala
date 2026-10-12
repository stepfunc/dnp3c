// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application

import java.io.{ File, IOException, PrintWriter }
import java.nio.charset.StandardCharsets

object TextFile {

  // writes the file in UTF-8, failing if the write or close did: PrintWriter doesn't throw when they fail
  def write(file: File)(body: PrintWriter => Unit): Unit = {
    val writer = new PrintWriter(file, StandardCharsets.UTF_8)
    try body(writer)
    finally writer.close()
    if (writer.checkError()) throw new IOException(s"Failed to write $file")
  }
}
