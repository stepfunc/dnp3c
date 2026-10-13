// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.helpers

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group20Var0, Group21Var0 }
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.protocol.parsing.Field

object FrozenCounterSteps {

  // frozen counter objects from Table 8-6, and at least one of them
  private val frozenCounters = Validations.verifyOnlyTable86Contents && Validations.isNotEmpty.acrossFragments

  def binaryCounterFreezeSteps(ack: Boolean): Iterable[LabeledStep] = {
    List(
      1 --> send(getFreezeFunctionCode(ack), Sequence.one, AllObjectsHeader(Group20Var0)),
      2 --> expectedFreezeResponse(ack, Sequence.one),
      3 --> send(Requests.readAllObjects(Sequence.two, Group21Var0)),
      4 --> expectAtLeastOneResponse(Sequence.two, frozenCounters),
      5 --> PluginSteps.recordCurrentCounterValues(),
      6 --> PluginSteps.generateCounterInputData(),
      7 --> send(Requests.readAllObjects(Sequence.three, Group21Var0)),
      (8 to 11) --> expectAtLeastOneResponse(
        Sequence.three,
        frozenCounters && Validations.verifyFlagsComplyWithFlagNote &&
          PluginValidations.verifyRecordedCounterValues().acrossFragments
      ),
      12 --> send(getFreezeFunctionCode(ack), Sequence.four, AllObjectsHeader(Group20Var0)),
      13 --> expectedFreezeResponse(ack, Sequence.four),
      14 --> send(Requests.readAllObjects(Sequence.five, Group21Var0)),
      (15 to 16) --> expectAtLeastOneResponse(
        Sequence.five,
        frozenCounters && PluginValidations.verifyAllCountersCurrentValues().acrossFragments)
    )
  }

  def binaryCounterFreezeAndClearSteps(ack: Boolean): Iterable[LabeledStep] = {
    List(
      1 --> PluginSteps.generateCounterInputData(),
      2 --> send(getFreezeAndClearFunctionCode(ack), Sequence.one, AllObjectsHeader(Group20Var0)),
      3 --> expectedFreezeResponse(ack, Sequence.one),
      4 --> send(Requests.readAllObjects(Sequence.two, Group21Var0)),
      (5 to 8) --> expectAtLeastOneResponse(
        Sequence.two,
        frozenCounters && Validations.verifyFlagsComplyWithFlagNote && verifyAllNonZero),
      9 --> send(getFreezeAndClearFunctionCode(ack), Sequence.three, AllObjectsHeader(Group20Var0)),
      10 --> expectedFreezeResponse(ack, Sequence.three),
      11 --> send(Requests.readAllObjects(Sequence.four, Group21Var0)),
      (12 to 13) --> expectAtLeastOneResponse(
        Sequence.four,
        frozenCounters && Validations.verifyFlagsComplyWithFlagNote && verifyAllZero)
    )
  }

  private def getFreezeFunctionCode(ack: Boolean) = if (ack) AppFunction.freeze else AppFunction.freezeNoAck

  private def getFreezeAndClearFunctionCode(ack: Boolean) =
    if (ack) AppFunction.freezeClear else AppFunction.freezeClearNoAck

  private def expectedFreezeResponse(ack: Boolean, seq: Sequence): TestStep =
    if (ack) expectNullResponse(seq) else expectNoResponse

  private[helpers] def verifyAllNonZero: MessageValidation = verifyCounts(
    "Verify that every returned value is nonzero",
    (count: Long) => {
      if (count == 0) {
        throw new Exception("Unexpected zero value.")
      }
    })

  private[helpers] def verifyAllZero: MessageValidation = verifyCounts(
    "Verify that the values returned are all zero",
    (count: Long) => {
      if (count != 0) {
        throw new Exception("Unexpected non zero value.")
      }
    })

  private def verifyCounts(desc: String, countValidation: Long => Unit): MessageValidation =
    MessageValidation.check(desc) { message =>
      Validations.forEachField(message) {
        case (_, count: Field.Types.U32) => countValidation(count.value.value)
        case (_, count: Field.Types.U16) => countValidation(count.value.value)
        case _ =>
      }
    }
}
