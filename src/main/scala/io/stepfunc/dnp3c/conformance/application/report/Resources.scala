// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application.report

import java.io.{ File, FileOutputStream }

import scala.annotation.tailrec

object Resources {

  def read(resource: String): String = {
    val input = Resources.getClass.getClassLoader.getResourceAsStream(resource)
    try new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    finally input.close()
  }

  def extract(resource: String, destination: File): Unit = {

    val input = Resources.getClass.getClassLoader.getResourceAsStream(resource)
    val output = new FileOutputStream(destination)
    val buffer = new Array[Byte](2048)

    @tailrec
    def transfer(): Unit = {
      val num = input.read(buffer)
      if (num > 0) {
        output.write(buffer, 0, num)
        transfer()
      }
    }

    try {
      transfer()
    } finally {
      output.close()
      input.close()
    }

  }

}
