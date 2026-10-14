// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.common

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.Validations._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.CommandStatus
import io.stepfunc.dnp3c.dnp3.transport.Transport
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE

// several command objects in one request (8.2.4, 8.4.4)
class MultiOperationSteps(output: OperationType) {

  def steps(options: OptionSource): List[LabeledStep] = {
    val config = output.primary(options)
    val installed = output.installedPoints(options)
    val max = output.maxPerRequest(options)

    // a request for the points, each with the configured command; the last one's status is last
    def header(points: List[Index], last: CommandStatus = CommandStatus.SUCCESS): TwoByteCountAndIndexHeader = {
      val statuses = List.fill(points.size - 1)(CommandStatus.SUCCESS) :+ last
      TwoByteCountAndIndexHeader(
        config.command.variation,
        UInt16LE(points.size),
        PrefixedPayload.U16(points.zip(statuses).map { case (index, status) =>
          Prefix(UInt16LE(index.value), output.withStatus(config.command, status).toFixedSizeObject)
        })
      )
    }

    val steps1to6 =
      if (max <= 1) {
        List(1 --> describe("Device does not support multiple command objects in a single request, skipping to step 7"))
      } else {
        if (installed.isEmpty)
          throw new Exception("No installed points configured for the multiple object request test")
        // at most 16 commands
        val points = installed.take(List(installed.length, max, 16).min)
        val request = header(points)
        List(
          1 --> describe("Device supports up to " + max + " command objects in a single request"),
          2 --> describe("Sending " + points.size + " commands") + send(AppFunction.select, Sequence.zero, request),
          3 --> expectSingleResponse(Sequence.zero, echoes(request)),
          4 --> send(AppFunction.operate, Sequence.one, request),
          5 --> expectSingleResponse(Sequence.one, echoes(request)),
          6 --> output.checkOperate(points.map(_.value))
        )
      }

    // the DUT's maximum plus one more command, if there are enough points, and they fit in one link frame
    lazy val tooMany = installed.take(max) :+ installed.last
    val steps7to12 =
      if (max >= installed.length) {
        List(7 --> describe("max commands per request >= number of installed points. Skipping to step 13"))
      } else if (Message.request(Sequence.two, AppFunction.select, List(header(tooMany))).toBytes.size >
          Transport.maxPayload) {
        List(7 --> describe("max commands per request + 1 don't fit in one link frame. Skipping to step 13"))
      } else {
        // one more command than the maximum, the last for the last installed point
        val request = header(tooMany)
        val reply = header(tooMany, CommandStatus.TOO_MANY_OPS)
        List(
          7 --> describe("number of installed points > max commands per request"),
          8 --> send(AppFunction.select, Sequence.two, request),
          9 --> expectSingleResponse(Sequence.two, echoes(reply)),
          // a device that supports controls must support direct operate (8.2, 8.4), so step 10 never skips
          10 --> describe("Devices that support control operations must support direct operate"),
          11 --> send(AppFunction.directOp, Sequence.three, request),
          12 --> expectSingleResponse(Sequence.three, echoes(reply)) +
            output.checkOperate(installed.take(max).map(_.value)) +
            describe("Not verified by this tool: that the last point, refused with status 8, didn't operate")
        )
      }

    val uninstalled = output.uninstalledIndex(options)
    val steps13to14 = List(
      13 --> send(AppFunction.select, Sequence.four, List(config.twoByteHeader, config.twoByteHeader(uninstalled))),
      14 --> expectSingleResponse(
        Sequence.four,
        containing(IIN.PARAM_ERROR) && matches(List(
          config.twoByteHeader,
          config.twoByteHeader(uninstalled, c => output.withStatus(c, CommandStatus.NOT_SUPPORTED))))
      )
    )

    steps1to6 ::: steps7to12 ::: steps13to14
  }
}
