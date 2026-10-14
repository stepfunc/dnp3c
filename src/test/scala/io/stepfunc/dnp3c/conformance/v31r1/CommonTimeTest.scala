// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.s8
import io.stepfunc.dnp3c.conformance.api.{ Options, ProcedureDefinition }
import io.stepfunc.dnp3c.conformance.api.options.TimeSyncOptions
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// 3.1r1's 8.15: a read of one event with relative time, and 2.7's 8.15.2, numbered as 3.1r1's 8.15.3
@RunWith(classOf[JUnitRunner])
class CommonTimeTest extends AnyFunSuite with Matchers {

  private val relative = Map(TimeSyncOptions.binaryInputChangeWithRelativeTime.id -> "true")

  private def labels(procedure: ProcedureDefinition, options: Map[String, String] = relative): List[String] =
    procedure.configure(Options.from(options)).steps.map(_.label.stripPrefix("step: ")).toList.filter(_.head.isDigit)

  private def describes(procedure: ProcedureDefinition, options: Map[String, String]): String =
    procedure.configure(Options.from(options)).steps.flatMap(_.step.description).mkString("\n")

  test("8.15.3 is 2.7's 8.15.2 without its first step, so every step is one earlier") {
    labels(s8.s815.Procedure8152).head should equal("1")
    labels(s8.s815.Procedure8152).last should equal("38")
    labels(CommonTime.relativeTimeSupported).head should equal("1")
    labels(CommonTime.relativeTimeSupported).last should equal("37")
    describes(CommonTime.relativeTimeSupported, relative) should not include "DUT supports Binary Input Change"
  }

  test("8.15.2 accepts IIN2.1 from a device configured to reject reads of relative time events, and notes it") {
    val rejecting = Map(
      TimeSyncOptions.binaryInputChangeWithRelativeTime.id -> "false",
      TimeSyncOptions.binaryInputChangeWithRelativeTimeRejected.id -> "true")
    describes(CommonTime.readWithRelativeTime, rejecting) should include("Note the result")
    describes(CommonTime.readWithRelativeTime, relative) should not include "Note the result"
  }

  test("8.15.3 runs where 8.15.2 gets events with relative time: with time recorded, and the read not rejected") {
    def runs(settings: (String, String)*) =
      CommonTime.relativeTimeSupported.disabledBy(Options.from(settings.toMap)).isEmpty
    val (relative, absolute, rejected) = (
      TimeSyncOptions.binaryInputChangeWithRelativeTime.id,
      TimeSyncOptions.binaryInputChangeWithTime.id,
      TimeSyncOptions.binaryInputChangeWithRelativeTimeRejected.id)
    runs(relative -> "true", absolute -> "false") should equal(true)
    runs(relative -> "false", absolute -> "true") should equal(true)
    runs(relative -> "false", absolute -> "false") should equal(false)
    runs(relative -> "false", absolute -> "true", rejected -> "true") should equal(false)
  }
}
