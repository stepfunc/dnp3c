// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s6.s66.s662

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s6._
import io.stepfunc.dnp3c.conformance.s6.s66.s662
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.link.{ Control, LinkFrame }
import io.stepfunc.dnp3c.dnp3.transport.Transport
import io.stepfunc.dnp3c.dnp3.{ Comment, Requests, TestDriver }
import io.stepfunc.dnp3c.protocol.Hex
import io.stepfunc.dnp3c.dnp3.DeferredCheck

object Procedure6621 extends ProcedureDefinition {

  def readClass0CustomStartOctets(first: Byte, second: Byte): TestStep = new TestStep {

    def description: List[Comment] = {
      val sync1 = Hex.prefixed(first)
      val sync2 = Hex.prefixed(second)
      List(
        "Build a class 0 read (g60v1, qualifier 0x06) in a confirmed user data frame (control 0xD3)",
        s"Send the frame with its start octets replaced by {$sync1, $sync2}",
        "Recompute the header CRC so it matches the altered start octets"
      )
    }

    def run(driver: TestDriver): List[DeferredCheck] = {
      driver.write(s662.Section.class0Frame(driver).toBytesWithStart(first, second))
      Nil
    }
  }

  def stepsWithStartOctets(first: Byte, second: Byte): Iterable[LabeledStep] = {
    List(
      (1 to 4) --> readClass0CustomStartOctets(first, 0x64.toByte),
      (5 to 6) --> expectLinkTimeout(Some(s662.Section.severalSecondsMs)),
      (7 to 9) --> readClass0CustomStartOctets(0x05.toByte, second),
      (10 to 11) --> expectLinkTimeout(Some(s662.Section.severalSecondsMs))
    )
  }

  override def steps(options: OptionSource): Iterable[LabeledStep] = {
    val step12 = 12 --> describe("Run the checks above again with other bad start octets")
    s662.Section.prologue ++ stepsWithStartOctets(0x09.toByte, 0xFF.toByte) ++ List(step12) ++
      stepsWithStartOctets(0x01.toByte, 0xAA.toByte)
  }

}
