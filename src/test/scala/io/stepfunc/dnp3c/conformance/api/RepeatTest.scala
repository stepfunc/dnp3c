// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api

import io.stepfunc.dnp3c.dnp3.StubDriver
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class RepeatTest extends AnyFunSuite with Matchers {

  private object Driver extends StubDriver(StubDriver.reporter())

  test("runs a fresh step each time") {
    var built = 0
    var ran = 0
    val step = repeat(3) {
      built += 1
      new TestStep {
        def description = Nil
        def run(driver: io.stepfunc.dnp3c.dnp3.TestDriver) = { ran += 1; Nil }
      }
    }
    step.run(Driver)
    (built, ran) should equal((3, 3))
  }

  test("repeating zero times describes and runs nothing") {
    val step = repeat(0)(fail("the step was built"))
    noException should be thrownBy step.description
    step.run(Driver) should equal(Nil)
  }
}
