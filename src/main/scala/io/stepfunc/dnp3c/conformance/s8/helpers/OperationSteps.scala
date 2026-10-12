// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.helpers

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3.app._

class OperationSteps(opType: OperationType) {

  def expectOperation(index: Int): PluginCheck = opType.checkOperate(index)
  val expectNoOperation: PluginCheck = opType.checkNotOperate

  private def output(index: Int): String = s"${opType.name} $index"

  // Where the next OPERATE must follow the previous one before the select times out, a manual tester can't stop to
  // answer whether the output operated: they watch it throughout, and are asked after the steps.
  private def watch(index: Int, expected: String): TestStep = PluginSteps.prepare(
    s"Watch ${output(index)} during this procedure: $expected. You'll be asked what you saw after the steps, so " +
      "make sure you can tell one operation from two (e.g. a counter, or an on-time long enough to see). Get ready, " +
      "then press ENTER.")

  // a DIRECT OPERATE that completes successfully: the DUT echoes the request
  def directOperate(header: ObjectHeader, seq: Sequence): TestStep =
    send(AppFunction.directOp, seq, header) + expectSingleResponse(seq, echoes(header))

  object SBO {

    // echoStep is the step that checks the select's echo: 3 in x.2.1, whose step 2 is for a DUT without the outputs,
    // and 2 in x.2.2
    def supported(header: ObjectHeader, index: Int, echoStep: Int): Iterable[LabeledStep] = List(
      1 --> send(AppFunction.select, Sequence.zero, header),
      echoStep --> expectSingleResponse(Sequence.zero, echoes(header)),
      (echoStep + 1) --> send(AppFunction.operate, Sequence.one, header),
      (echoStep + 2) --> expectSingleResponse(Sequence.one, echoes(header)),
      (echoStep + 3) --> expectOperation(index)
    )

    def unsupported(header: ObjectHeader): Iterable[LabeledStep] = List(
      1 --> send(AppFunction.select, Sequence.zero, header),
      2 --> expectSingleResponse(Sequence.zero, containing(IIN.OBJECT_UNKNOWN) && without(IIN.PARAM_ERROR))
    )
  }

  object DO {

    def supported(header: ObjectHeader, index: Int): Iterable[LabeledStep] = List(
      1 --> send(AppFunction.directOp, Sequence.zero, header),
      3 --> expectSingleResponse(Sequence.zero, echoes(header)),
      4 --> expectOperation(index)
    )

    def unsupported(header: ObjectHeader): Iterable[LabeledStep] = List(
      1 --> send(AppFunction.directOp, Sequence.zero, header),
      2 --> expectSingleResponse(Sequence.zero, containing(IIN.OBJECT_UNKNOWN) && without(IIN.PARAM_ERROR))
    )

  }

  def stepsSelectRetrySameSequence(header: ObjectHeader, index: Int): Iterable[LabeledStep] = List(
    2 --> send(AppFunction.select, Sequence.zero, header),
    3 --> expectSingleResponse(Sequence.zero, echoes(header)),
    4 --> send(AppFunction.select, Sequence.zero, header),
    5 --> expectSingleResponse(Sequence.zero, echoes(header)),
    6 --> send(AppFunction.operate, Sequence.one, header),
    7 --> expectSingleResponse(Sequence.one, echoes(header)),
    8 --> expectOperation(index)
  )

  def stepsSelectRetryIncrementedSequence(header: ObjectHeader, index: Int): Iterable[LabeledStep] = List(
    2 --> send(AppFunction.select, Sequence.zero, header),
    3 --> expectSingleResponse(Sequence.zero, echoes(header)),
    4 --> send(AppFunction.select, Sequence.one, header),
    5 --> expectSingleResponse(Sequence.one, echoes(header)),
    6 --> send(AppFunction.operate, Sequence.two, header),
    7 --> expectSingleResponse(Sequence.two, echoes(header)),
    8 --> expectOperation(index)
  )

  def stepsSameSequenceNumberOperateRetry(header: ObjectHeader, index: Int): Iterable[LabeledStep] = List(
    2 --> watch(
      index,
      "it should operate once after the OPERATE in step 4, and not again after the repeated one in " +
        "step 7") + send(AppFunction.select, Sequence.zero, header),
    3 --> expectSingleResponse(Sequence.zero, echoes(header)),
    4 --> send(AppFunction.operate, Sequence.one, header),
    5 --> expectSingleResponse(Sequence.one, echoes(header)),
    6 --> expectOperation(index).deferrable,
    7 --> send(AppFunction.operate, Sequence.one, header),
    8 --> expectSingleResponse(Sequence.one, echoes(header)),
    9 --> expectNoOperation.deferrable
  )

  def stepsIncrementingSequenceNumberOperateRetry(
      header: ObjectHeader,
      index: Int,
      failure: ObjectHeader): Iterable[LabeledStep] = List(
    2 --> watch(index, "it should operate once after the OPERATE in step 4, and not again after the one in step 7") +
      send(AppFunction.select, Sequence.zero, header),
    3 --> expectSingleResponse(Sequence.zero, echoes(header)),
    4 --> send(AppFunction.operate, Sequence.one, header),
    5 --> expectSingleResponse(Sequence.one, echoes(header)),
    6 --> expectOperation(index).deferrable,
    7 --> send(AppFunction.operate, Sequence.two, header),
    8 --> expectSingleResponse(Sequence.two, matches(failure)),
    9 --> expectNoOperation.deferrable
  )

  def stepsSequenceNumbersOutOfOrder(header: ObjectHeader, index: Int, failure: ObjectHeader): Iterable[LabeledStep] =
    List(
      2 --> watch(index, "it should not operate at all, after the OPERATE in step 4 or the one in step 7") +
        send(AppFunction.select, Sequence.zero, header),
      3 --> expectSingleResponse(Sequence.zero, echoes(header)),
      4 --> send(AppFunction.operate, Sequence.two, header),
      5 --> expectSingleResponse(Sequence.two, matches(failure)),
      6 --> expectNoOperation.deferrable,
      7 --> send(AppFunction.operate, Sequence.one, header),
      8 --> expectSingleResponse(Sequence.one, matches(failure)),
      9 --> expectNoOperation.deferrable
    )

  def stepsUninstalledCommandPoint(
      function: AppFunction,
      request: ObjectHeader,
      reply: ObjectHeader): Iterable[LabeledStep] = List(
    1 --> send(function, Sequence.zero, request),
    2 --> expectSingleResponse(Sequence.zero, containing(IIN.PARAM_ERROR) && matches(reply)),
    3 --> expectNoOperation
  )

  def stepsSelectTimeout(timeoutMs: Int, header: ObjectHeader, reply: ObjectHeader): Iterable[LabeledStep] = List(
    1 --> send(AppFunction.select, Sequence.zero, header),
    2 --> expectSingleResponse(Sequence.zero, echoes(header)),
    // 1 second past the timeout
    3 --> delay(timeoutMs + 1000),
    4 --> send(AppFunction.operate, Sequence.one, header) + expectSingleResponse(Sequence.one, matches(reply)),
    5 --> expectNoOperation
  )

  def stepsSBOMismatch(header1: ObjectHeader, header2: ObjectHeader, failure: ObjectHeader): Iterable[LabeledStep] =
    List(
      1 --> send(AppFunction.select, Sequence.zero, header1),
      2 --> expectSingleResponse(Sequence.zero, echoes(header1)),
      3 --> send(AppFunction.operate, Sequence.one, header2),
      4 --> expectSingleResponse(Sequence.one, matches(failure)),
      5 --> expectNoOperation
    )

}
