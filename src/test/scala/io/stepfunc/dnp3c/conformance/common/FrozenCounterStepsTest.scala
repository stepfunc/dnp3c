// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.common

import io.stepfunc.dnp3c.dnp3.Validate.passes
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class FrozenCounterStepsTest extends AnyFunSuite with Matchers {

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
