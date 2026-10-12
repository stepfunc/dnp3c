// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol

import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class ClockTest extends AnyFunSuite with Matchers {

  test("time left is rounded up to whole milliseconds, so a read can wait until the deadline") {
    Clock.ceilMillis(1) should equal(1)
    Clock.ceilMillis(1000000) should equal(1)
    Clock.ceilMillis(1000001) should equal(2)
    Clock.ceilMillis(250000000) should equal(250)
  }

  test("a deadline that has passed leaves no time") {
    Clock.ceilMillis(0) should equal(0)
    Clock.ceilMillis(-1) should equal(0)
    Clock.ceilMillis(-5000000) should equal(0)
  }

  test("a deadline counts down as the clock moves") {
    val clock = new VirtualClock
    val deadline = clock.deadline(1000)
    clock.msLeft(deadline) should equal(1000)
    clock.sleep(400)
    clock.msLeft(deadline) should equal(600)
    clock.advance(700)
    clock.msLeft(deadline) should equal(0)
  }
}
