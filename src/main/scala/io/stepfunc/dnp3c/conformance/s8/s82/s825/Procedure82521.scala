// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s82.s825

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ ControlOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.s8.helpers.{ OperationSteps, OperationType }
import io.stepfunc.dnp3c.dnp3.app.objects.ControlCode
import io.stepfunc.dnp3c.dnp3.app.MutableSequence

object Procedure82521 extends TestProcedure {

  override def id = "8.2.5.2.1"

  override def description = "Binary Output, Complementary Function Support"

  override protected def requiredOptions = List(SupportOptions.Outputs.binary.commands)

  def steps(options: OptionSource): Iterable[LabeledStep] = {
    val operations = new OperationSteps(OperationType.BinaryOutput)
    // each request gets its own sequence number, so a repeated control isn't taken for a retry
    val seq = MutableSequence.zero

    // from step first: describe the pair, then operate the point with each code in turn, checking it operates
    def operateEach(
        first: Int,
        last: Int,
        point: ControlOptions.ControlCodePointOptions,
        codes: List[ControlCode],
        pair: String): List[LabeledStep] = codes.flatMap(point.getConfig(options, _)) match {
      case Nil => List((first to last) --> describe(s"DUT does not support $pair."))
      case configs =>
        (first --> describe(s"DUT supports $pair.")) :: configs.zipWithIndex.flatMap { case (config, i) =>
          List(
            (first + 1 + 2 * i) --> operations.directOperate(config.oneByteHeader, seq.increment()),
            (first + 2 + 2 * i) --> operations.expectOperation(config.index.value))
        }
    }

    val latchPair = "Latch On/NUL and Latch Off/NUL pair"
    val pulsePair = "Pulse On/Close and Pulse On/Trip pair"
    val points = List(ControlOptions.latchOnOffPoint, ControlOptions.pulseOnOffPoint, ControlOptions.latchAndPulsePoint)

    if (points.exists(_.isPresent(options))) {
      operateEach(3, 7, ControlOptions.latchOnOffPoint, List(ControlCode.latchOn, ControlCode.latchOff), latchPair) ++
        operateEach(
          8,
          12,
          ControlOptions.pulseOnOffPoint,
          List(ControlCode.pulseClose, ControlCode.pulseTrip),
          pulsePair) ++
        operateEach(
          13,
          21,
          ControlOptions.latchAndPulsePoint,
          List(ControlCode.latchOn, ControlCode.pulseTrip, ControlCode.pulseClose, ControlCode.latchOff),
          s"both $latchPair and $pulsePair"
        )
    } else {
      List(
        LabeledStep(
          "Skipped",
          describe("The DUT has no complementary-function binary outputs installed; skipping this section.")
        )
      )
    }

  }

}
