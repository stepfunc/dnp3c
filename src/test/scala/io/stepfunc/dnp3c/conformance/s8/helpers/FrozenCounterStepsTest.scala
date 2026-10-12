// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.helpers

import io.stepfunc.dnp3c.dnp3.{ MessageValidation, StubDriver }
import io.stepfunc.dnp3c.dnp3.app.Message
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class FrozenCounterStepsTest extends AnyFunSuite with Matchers {

  private def passes(validation: MessageValidation, objects: String): Boolean = scala.util
    .Try(validation.validate(StubDriver.reporter(), Message.parse(Hex.from("C0 81 00 00 " + objects))))
    .isSuccess

  // g21v1 (32-bit) and g21v2 (16-bit) frozen counters at indexes 0 and 1, each ONLINE
  private def counters32(a: String, b: String) = s"15 01 00 00 01 01 $a 00 00 00 01 $b 00 00 00"
  private def counters16(a: String, b: String) = s"15 02 00 00 01 01 $a 00 01 $b 00"

  test("checks every value, 32- or 16-bit, is zero or every one is nonzero") {
    for (counters <- List(counters32, counters16)) {
      passes(FrozenCounterSteps.verifyAllZero, counters("00", "00")) should equal(true)
      passes(FrozenCounterSteps.verifyAllZero, counters("00", "05")) should equal(false)
      passes(FrozenCounterSteps.verifyAllNonZero, counters("03", "05")) should equal(true)
      passes(FrozenCounterSteps.verifyAllNonZero, counters("03", "00")) should equal(false)
    }
  }
}
