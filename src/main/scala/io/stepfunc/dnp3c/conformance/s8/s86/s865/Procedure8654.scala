// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s86.s865

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s6._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3.app.{ IIN, Sequence, Unsolicited }
import io.stepfunc.dnp3c.dnp3.link.Control
import io.stepfunc.dnp3c.dnp3.{ AutoConfirm, LinkOverride, Requests }
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE

object Procedure8654 extends TestProcedure {

  override def id: String = "8.6.5.4"

  override def description: String = "Test Procedure, Confirmed Response Options"

  private val control = Control.confirmedUserData(true)
  private val broadcast = UInt16LE(0xFFFE)
  private val broadcastNoConfirm = UInt16LE(0xFFFD)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] =
    stepsFor(broadcast, confirmed = true, options) ++
      List(14 --> describe(
        "Repeat the sequence with step 5's WRITE addressed to 0xFFFD. Verify that step 9 has CON=0 and step 10 has " +
          "IIN1.0=0; the application confirmation in step 11 is left out.")) ++
      stepsFor(broadcastNoConfirm, confirmed = false, options)

  // steps 1 to 13 with the WRITE broadcast to address: 0xFFFE asks the DUT to have its response confirmed, which
  // keeps IIN1.0 set until it is; 0xFFFD doesn't
  private def stepsFor(address: UInt16LE, confirmed: Boolean, options: OptionSource): List[LabeledStep] = {
    val confirmRequest = if (confirmed) verifyConfirmRequested else verifyConfirmNotRequested
    val broadcastStillSet = if (confirmed) containing(IIN.BROADCAST) else without(IIN.BROADCAST)
    List(
      1 --> PluginSteps.cyclePower,
      2 --> sendLinkControl(0xC0.toByte) + expectFrame(isConfirm),
      3 --> send(Requests.readClass1(Sequence.one)),
      4 --> expectAtLeastOneResponse(Sequence.one, containing(IIN.DEVICE_RESTART) && verifyConfirmRequestedIfRequired),
      5 --> send(
        Requests.clearRestartIIN(Sequence.two),
        LinkOverride.destination(address).withControlBlock(control.toByte)),
      (6 to 7) --> expectNoAnswer(options),
      8 --> send(Requests.readClass1(Sequence.three)),
      9 --> expectSingleResponse(
        Sequence.three,
        AutoConfirm.No,
        isNull && without(IIN.DEVICE_RESTART) && containing(IIN.BROADCAST) && confirmRequest),
      10 --> send(Requests.readClass1(Sequence.four)) +
        expectSingleResponse(Sequence.four, AutoConfirm.No, without(IIN.DEVICE_RESTART) && broadcastStillSet)
    ) ++ (if (confirmed) List(11 --> send(Requests.confirm(Sequence.four, Unsolicited.No))) else Nil) ++ List(
      12 --> send(Requests.readClass1(Sequence.five)),
      // the document's step 13 says IIN1-1 where it means IIN1-0, the bit steps 9 and 10 check
      13 -->
        expectSingleResponse(Sequence.five, AutoConfirm.No, isNull && without(IIN.DEVICE_RESTART | IIN.BROADCAST))
    )
  }

}
