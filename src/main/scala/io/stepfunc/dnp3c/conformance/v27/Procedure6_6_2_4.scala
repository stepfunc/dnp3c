// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.common.LinkSteps._
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.link._
import io.stepfunc.dnp3c.dnp3.transport.Transport
import io.stepfunc.dnp3c.dnp3.{ Requests, TestDriver }
import io.stepfunc.dnp3c.protocol.parsing.convert
import io.stepfunc.dnp3c.dnp3.DeferredCheck

object Procedure6_6_2_4 extends ProcedureDefinition {

  private def sendClass0InvalidHeaderCRC(offset: Short) = new TestStep {

    def description = List(
      "Build a class 0 read (g60v1, qualifier 0x06) in a confirmed user data frame (control 0xD3)",
      "Corrupt the header CRC",
      "Send the request"
    )

    def run(driver: TestDriver): List[DeferredCheck] = {
      driver.write(PrimaryFrames.class0Frame(driver).toBytesWithGen(InvalidCrc(offset), Crc))
      Nil
    }
  }

  private def sendClass0InvalidBodyCRC(offset: Short): TestStep = new TestStep {

    def description = List(
      "Keep the header CRC valid, but corrupt a CRC in the frame's data blocks",
      "Send the request"
    )

    def run(driver: TestDriver): List[DeferredCheck] = {
      driver.write(PrimaryFrames.class0Frame(driver).toBytesWithGen(Crc, InvalidCrc(offset)))
      Nil
    }
  }

  private def steps(offset: Short): Iterable[LabeledStep] = {
    List(
      (1 to 3) --> sendClass0InvalidHeaderCRC(offset),
      (4 to 5) --> expectLinkTimeout(Some(PrimaryFrames.severalSecondsMs)),
      (6 to 7) --> sendClass0InvalidBodyCRC(offset),
      (8 to 9) --> expectLinkTimeout(Some(PrimaryFrames.severalSecondsMs))
    )
  }

  override def steps(options: OptionSource): Iterable[LabeledStep] = {

    val step10 = 10 --> describe("Run the checks above again with other corrupt CRC values")

    PrimaryFrames.prologue ++ steps(1.toShort) ++ List(step10) ++ steps(2.toShort)

  }

}
