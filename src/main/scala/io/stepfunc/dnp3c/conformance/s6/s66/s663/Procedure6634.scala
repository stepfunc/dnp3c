// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s6.s66.s663

import io.stepfunc.dnp3c.conformance.api.options.{ LinkOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.api.{ Label, _ }
import io.stepfunc.dnp3c.conformance.s6._
import io.stepfunc.dnp3c.conformance.s6.s66.s663.Section.twice
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.link._
import io.stepfunc.dnp3c.dnp3.{ Comment, LinkOverride, TestDriver }
import io.stepfunc.dnp3c.protocol.parsing.UInt8
import io.stepfunc.dnp3c.dnp3.DeferredCheck

object Procedure6634 extends TestProcedure {

  override def id: String = "6.6.3.4"

  override def description: String = "Invalid CRC"

  override protected def requiredOptions = List(SupportOptions.dataLinkConfirmRequests, SupportOptions.dataLinkRetries)

  def sendConfirmWithInvalidCRC(offset: Short): TestStep = new TestStep {

    def description: List[Comment] = {
      List("Send an ACK frame with a corrupt CRC")
    }

    def run(driver: TestDriver): List[DeferredCheck] = {
      val generator = InvalidCrc(offset)
      driver.write(LinkFrame.headerOnly(Control.ack, driver.remote, driver.local).toBytesWithGen(generator, generator))
      Nil
    }
  }

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {

    val timeout = options.get(LinkOptions.timeout)
    val responseTimeout = Some(responseTimeoutMs(options))

    def steps(offset: Short): Iterable[LabeledStep] = {
      val response = new UnconfirmedFrame
      List(
        1 --> readClass0(Sequence.one, LinkOverride.control(0xC4.toByte)),
        2 --> response.expect(withFunction(LinkFunction.CONFIRMED_USER_DATA), responseTimeout),
        3 --> sendConfirmWithInvalidCRC(offset),
        4 --> (response.expectRetry(timeout) + disconnect)
      )
    }

    twice(
      options,
      steps(offset = 1),
      5 --> describe("Run the checks above again with another corrupt CRC"),
      steps(offset = 2))
  }
}
