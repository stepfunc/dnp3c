// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import java.io.StringReader

import io.stepfunc.dnp3c.conformance.Run
import io.stepfunc.dnp3c.conformance.api.ProcedureDefinition
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app.{ AppFunction, IIN, Message, Sequence }
import io.stepfunc.dnp3c.dnp3.link.LinkFrame
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// 3.1r1's 8.6.5 tests against an outstation with no events, which answers class reads with a Null Response
@RunWith(classOf[JUnitRunner])
class BroadcastTest extends AnyFunSuite with Matchers {

  /**
   * @param actsOnConfirmed whether it acts on a broadcast sent as confirmed user data, as well as unconfirmed
   * @param confirmFFFE whether it asks for confirmation of IIN1.0 after a broadcast to 0xFFFE, as it should
   * @param errors error IIN bits it sets in every response
   */
  private class Outstation(actsOnConfirmed: Boolean, confirmFFFE: Boolean = true, errors: IIN = IIN.empty)
      extends RespondingDriver(StubDriver.reporter()) {
    // the address of the broadcast to report, until it's reported (0xFFFF, 0xFFFD) or confirmed (0xFFFE)
    private var broadcast: Option[Int] = None
    private var awaitingConfirm: Option[Sequence] = None

    override def writeMessage(message: Message, link: LinkOverride): Unit = {
      val destination = link.destination.map(_.value)
      val confirmedUserData = link.controlBlock.exists(c => (c & 0x0F) == 0x03)
      if (destination.exists(_ >= 0xFFFD)) {
        if (!confirmedUserData || actsOnConfirmed) broadcast = destination
      } else if (message.function == AppFunction.confirm) {
        if (awaitingConfirm.contains(message.ctrl.seq)) broadcast = None
        awaitingConfirm = None
      } else {
        val iin = (if (broadcast.isDefined) IIN.BROADCAST else IIN.empty) | errors
        val con = broadcast.contains(0xFFFE) && confirmFFFE
        if (con) awaitingConfirm = Some(message.ctrl.seq)
        else if (broadcast.isDefined) broadcast = None
        // FIR and FIN set, CON as above, and the request's sequence number
        val ctrl = (0xC0 | (if (con) 0x20 else 0) | message.ctrl.seq.value).toByte
        enqueue(Message.parse(List(ctrl, 0x81.toByte, iin.lsb, iin.msb)))
      }
    }

    override def readFrame(timeout: Option[Int]): Option[LinkFrame] = timedOut(timeout)
  }

  // runs the steps in order, stopping at the first that fails
  private def passes(procedure: ProcedureDefinition, dut: Outstation): Boolean =
    Console.withIn(new StringReader(""))(
      Run.passes(procedure, dut))

  test("8.6.5a passes an outstation that acts on a broadcast sent as confirmed user data") {
    passes(Broadcast.allStationsIndication, new Outstation(actsOnConfirmed = true)) should equal(true)
  }

  test("8.6.5a passes one that acts only on unconfirmed user data, by sending the broadcast again (step 5)") {
    passes(Broadcast.allStationsIndication, new Outstation(actsOnConfirmed = false)) should equal(true)
  }

  test("8.6.5a fails a response with IIN1.0 that also has an error bit") {
    passes(
      Broadcast.allStationsIndication,
      new Outstation(actsOnConfirmed = true, errors = IIN.FUNC_NOT_SUPPORTED)) should equal(false)
  }

  test("8.6.5b passes an outstation that asks for confirmation after 0xFFFE, and not after 0xFFFD") {
    passes(Broadcast.confirmedResponseOptions, new Outstation(actsOnConfirmed = false)) should equal(true)
  }

  test("8.6.5b fails one that doesn't ask for confirmation after 0xFFFE") {
    passes(Broadcast.confirmedResponseOptions, new Outstation(actsOnConfirmed = true, confirmFFFE = false)) should
      equal(false)
  }
}
