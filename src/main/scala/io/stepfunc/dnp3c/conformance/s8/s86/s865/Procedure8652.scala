// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s86.s865

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s6._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3.app.{ IIN, Sequence }
import io.stepfunc.dnp3c.dnp3.link.Control
import io.stepfunc.dnp3c.dnp3.{ AutoConfirm, LinkOverride, Requests }
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE

object Procedure8652 extends TestProcedure {

  override def id: String = "8.6.5.2"

  override def description: String = "Test Procedure"

  private val control = Control.confirmedUserData(true)
  private val broadcast = UInt16LE(0xFFFF)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    List(
      1 --> PluginSteps.cyclePower,
      2 --> sendLinkControl(0xC0.toByte) + expectFrame(isConfirm),
      3 --> send(Requests.readClass1(Sequence.one)),
      4 --> expectAtLeastOneResponse(Sequence.one, containing(IIN.DEVICE_RESTART) && verifyConfirmRequestedIfRequired),
      5 --> send(
        Requests.clearRestartIIN(Sequence.two),
        LinkOverride.destination(broadcast).withControlBlock(control.toByte)),
      (6 to 7) --> expectNoAnswer(options),
      8 --> send(Requests.readClass1(Sequence.three)),
      (9 to 10) -->
        expectAtLeastOneResponse(
          Sequence.three,
          (without(IIN.DEVICE_RESTART) && verifyConfirmRequestedIfRequired).toResponseValidation &&
            containing(IIN.BROADCAST).onFirstFragment),
      11 --> send(Requests.readClass1(Sequence.four)),
      12 --> expectSingleResponse(Sequence.four, isNull && without(IIN.DEVICE_RESTART | IIN.BROADCAST))
    )
  }

}
