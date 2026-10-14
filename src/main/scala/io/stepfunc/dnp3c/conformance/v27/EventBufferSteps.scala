// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.common.EventClassData
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.Validations._
import io.stepfunc.dnp3c.dnp3.app.{ IIN, Sequence }
import io.stepfunc.dnp3c.dnp3.{ AutoConfirm, MessageValidation, Requests }

object EventBufferSteps {

  def get(
      name: String,
      maxEvents: Int,
      eventClass: EventClassData,
      generateEventsStep: (Int) => TestStep,
      singleEventVerification: MessageValidation): List[LabeledStep] = {
    List(
      1 --> describe(s"Skipped for a device without $name"),
      2 --> send(Requests.readClass123(Sequence.zero)) + expectEventsEmptied(Sequence.zero),
      3 --> generateEventsStep(maxEvents),
      4 --> send(eventClass.read(Sequence.one)),
      5 --> readResponseFragment(Sequence.one, isFirstFragment && without(IIN.EVENT_BUFFER_OVERFLOW)), // no confirm
      6 --> generateEventsStep(1),
      7 --> send(Requests.readCount(Sequence.two, eventClass.variation, 1, twoByte = false)),
      (8 to 9) --> expectSingleResponse(
        Sequence.two,
        AutoConfirm.Yes,
        singleEventVerification && containing(IIN.EVENT_BUFFER_OVERFLOW) && verifyConfirmRequested),
      10 --> send(Requests.readCount(Sequence.three, eventClass.variation, 1, twoByte = false)),
      (11 to 12) --> expectSingleResponse(
        Sequence.three,
        AutoConfirm.Yes,
        singleEventVerification && without(IIN.EVENT_BUFFER_OVERFLOW) && verifyConfirmRequested)
    )
  }

}
