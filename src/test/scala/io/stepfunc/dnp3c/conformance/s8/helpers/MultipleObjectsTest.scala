// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.helpers

import io.stepfunc.dnp3c.conformance.Placements.id
import io.stepfunc.dnp3c.conformance.api.{ Options, ProcedureDefinition }
import io.stepfunc.dnp3c.conformance.api.options.{ AnalogOutputOptions, ControlOptions }
import io.stepfunc.dnp3c.conformance.s8.s82.s824.Procedure8242
import io.stepfunc.dnp3c.conformance.s8.s84.s844.Procedure8442
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.CommandStatus
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.mutable

// 8.2.4.2 and 8.4.4.2: several command objects in one request, against a model outstation
@RunWith(classOf[JUnitRunner])
class MultipleObjectsTest extends AnyFunSuite with Matchers {

  private class Plugin extends DefaultIntegrationPlugin {
    override def checkBinaryOutputOperate(reporter: CheckReporter, index: Int): Option[DeferredCheck] = None
    override def checkAnalogOutputOperate(reporter: CheckReporter, index: Int): Option[DeferredCheck] = None
  }

  // records each header of each request as (function, number of objects)
  // an outstation with these installed points, that accepts up to max commands in a request: it echoes each
  // command with NOT_SUPPORTED for a point it doesn't have, TOO_MANY_OPS past its maximum, and SUCCESS otherwise
  private class Outstation(
      output: OperationType,
      options: Options,
      installed: Set[Int],
      max: Int,
      tooMany: CommandStatus) extends RespondingDriver(StubDriver.reporter(new Plugin)) {
    val requests = mutable.ListBuffer.empty[(AppFunction, Int)]
    // the configured command, which every request must carry
    private val command = output.primary(options).command

    override def writeMessage(message: Message, options: LinkOverride): Unit = {
      val headers = message.headers.map {
        case TwoByteCountAndIndexHeader(variation, count, PrefixedPayload.U16(objects)) =>
          requests += ((message.function, objects.size))
          objects.foreach(obj => obj.instance.obj.toBytes should equal(command.toFixedSizeObject.toBytes))
          val replies = objects.zipWithIndex.map { case (obj, position) =>
            val status =
              if (!installed.contains(obj.index.value)) CommandStatus.NOT_SUPPORTED
              else if (position >= max) tooMany
              else CommandStatus.SUCCESS
            (obj.index, status)
          }
          (variation, count, replies)
        case header => fail(s"unexpected header $header")
      }
      val notSupported = headers.exists(_._3.exists(_._2 == CommandStatus.NOT_SUPPORTED))
      respond(message, if (notSupported) IIN.PARAM_ERROR else IIN.empty, headers.map(reply))
    }

    private def reply(header: (Variation, UInt16LE, List[(UInt16LE, CommandStatus)])): ObjectHeader = {
      TwoByteCountAndIndexHeader(
        header._1,
        header._2,
        PrefixedPayload.applyU16(header._3.map { case (index, status) =>
          Prefix(index, output.withStatus(command, status).toFixedSizeObject)
        }))
    }

    override def close(): Unit = {}
  }

  private case class Family(
      procedure: ProcedureDefinition,
      output: OperationType,
      installedKey: String,
      maxKey: String,
      uninstalledKey: String,
      command: Map[String, String])

  private val families = List(
    Family(
      Procedure8242,
      OperationType.BinaryOutput,
      ControlOptions.installedPoints.id,
      ControlOptions.maxControlPointsInSingleRequest.id,
      ControlOptions.uninstalledIndex.id,
      // a command other than the default
      Map(
        s"${ControlOptions.prefix}.primary.valid.count" -> "3",
        s"${ControlOptions.prefix}.primary.valid.onTime.ms" -> "250",
        s"${ControlOptions.prefix}.primary.valid.offTime.ms" -> "300"
      )
    ),
    Family(
      Procedure8442,
      OperationType.AnalogOutput,
      AnalogOutputOptions.installedPoints.id,
      AnalogOutputOptions.maxAnalogOutputsInSingleRequest.id,
      AnalogOutputOptions.uninstalledIndex.id,
      Map(s"${AnalogOutputOptions.prefix}.primary.valid.value" -> "1234")
    )
  )

  private def run(
      family: Family,
      installed: Range,
      max: Int,
      tooMany: CommandStatus = CommandStatus.TOO_MANY_OPS): List[(AppFunction, Int)] = {
    val options = Options.from(
      family.command ++ Map(family.installedKey -> installed.mkString(","), family.maxKey -> max.toString))
    val outstation = new Outstation(family.output, options, installed.toSet, max, tooMany)
    family.procedure.configure(options).steps.foreach(_.step.run(outstation))
    outstation.requests.toList
  }

  test("sends the installed points up to the maximum, then one more than the maximum, then an installed and an uninstalled point") {
    for (family <- families)
      withClue(family.procedure.id) {
        run(family, 0 to 9, max = 4) should equal(List(
          (AppFunction.select, 4),
          (AppFunction.operate, 4),
          (AppFunction.select, 5),
          (AppFunction.directOp, 5),
          (AppFunction.select, 1),
          (AppFunction.select, 1)))
      }
  }

  test("sends at most 16 commands, and skips the request past the maximum if every point fits") {
    for (family <- families)
      withClue(family.procedure.id) {
        run(family, 0 to 19, max = 20) should equal(
          List(
            (AppFunction.select, 16),
            (AppFunction.operate, 16),
            (AppFunction.select, 1),
            (AppFunction.select, 1)))
      }
  }

  test("skips the request past the maximum if it doesn't fit in one link frame") {
    // 19 binary output commands (13 bytes each with their index) don't fit; 19 analog output commands (5 bytes) do
    val Seq(binary, analog) = families
    run(binary, 0 to 29, max = 18) should equal(
      List((AppFunction.select, 16), (AppFunction.operate, 16), (AppFunction.select, 1), (AppFunction.select, 1)))
    run(analog, 0 to 29, max = 18) should equal(List(
      (AppFunction.select, 16),
      (AppFunction.operate, 16),
      (AppFunction.select, 19),
      (AppFunction.directOp, 19),
      (AppFunction.select, 1),
      (AppFunction.select, 1)))
  }

  test("skips the multiple-object request for a DUT that accepts one command at a time") {
    for (family <- families)
      withClue(family.procedure.id) {
        run(family, 0 to 2, max = 1) should equal(
          List(
            (AppFunction.select, 2),
            (AppFunction.directOp, 2),
            (AppFunction.select, 1),
            (AppFunction.select, 1)))
      }
  }

  test("a DUT that accepts more commands than its maximum fails") {
    for (family <- families)
      withClue(family.procedure.id) {
        an[Exception] should be thrownBy run(family, 0 to 9, max = 4, tooMany = CommandStatus.SUCCESS)
      }
  }

  test("a DUT that accepts several commands needs installed points configured") {
    for (family <- families)
      withClue(family.procedure.id) {
        an[Exception] should be thrownBy
          family.procedure
            .configure(Options.from(Map(family.installedKey -> "", family.maxKey -> "4")))
            .steps
            .toList
      }
  }
}
