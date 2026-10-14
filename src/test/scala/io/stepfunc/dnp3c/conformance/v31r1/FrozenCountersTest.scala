// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import scala.util.Try

import io.stepfunc.dnp3c.conformance.api.Options
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.dnp3.{ ResponseValidation, StubDriver }
import io.stepfunc.dnp3c.dnp3.app.Message
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// 3.1r1's freeze tests: a device with frozen counter events reads them with the frozen counters after each freeze
@RunWith(classOf[JUnitRunner])
class FrozenCountersTest extends AnyFunSuite with Matchers {

  // g23v1 and g21v1 at index 0, flags 0x01, with the given count
  private def event(count: Int) = "17 01 17 01 00 01 %02X 00 00 00".format(count)
  private def frozen(count: Int) = "15 01 00 00 00 01 %02X 00 00 00".format(count)
  private val frozenAt1 = "15 01 00 01 01 01 05 00 00 00"

  private def passes(v: ResponseValidation, fragments: String*): Boolean =
    Try(v.validate(StubDriver.reporter(), fragments.map(f => Message.parse(Hex.from(s"E0 81 00 00 $f"))).toList))
      .isSuccess

  test("the response reports the frozen counter events first, then the frozen counters") {
    passes(FrozenCounters.eventsThenFrozenCounters, s"${event(5)} ${frozen(5)}") should equal(true)
    // across fragments too
    passes(FrozenCounters.eventsThenFrozenCounters, event(5), frozen(5)) should equal(true)
    passes(FrozenCounters.eventsThenFrozenCounters, s"${frozen(5)} ${event(5)}") should equal(false)
    passes(FrozenCounters.eventsThenFrozenCounters, frozen(5)) should equal(false)
    passes(FrozenCounters.eventsThenFrozenCounters, event(5)) should equal(false)
  }

  test("each point is in both objects, its latest event with the frozen value where the step asks") {
    passes(FrozenCounters.bothObjects(sameValues = true), s"${event(5)} ${frozen(5)}") should equal(true)
    passes(FrozenCounters.bothObjects(sameValues = true), s"${event(4)} ${frozen(5)}") should equal(false)
    passes(FrozenCounters.bothObjects(sameValues = false), s"${event(4)} ${frozen(5)}") should equal(true)
    // an earlier event of the point may come first
    passes(FrozenCounters.bothObjects(sameValues = true), s"${event(4)} ${event(5)} ${frozen(5)}") should equal(true)
    passes(FrozenCounters.bothObjects(sameValues = false), s"${event(5)} $frozenAt1") should equal(false)
  }

  test("a device with frozen counter events reads them with the frozen counters; one without, the counters alone") {
    def labels(events: Boolean) = FrozenCounters.freeze
      .configure(Options.from(Map(Inputs.frozenCounter.events.id -> events.toString)))
      .steps
      .map(_.label)
    labels(events = true) should contain allOf ("step: 3", "steps: 3.1 to 3.3", "steps: 14.2 to 16")
    labels(events = false) should contain allOf ("step: 4.1", "step: 4.2", "steps: 15.2 to 16")
    labels(events = false) should not contain "step: 3"
  }

  test("after the second freeze and clear, frozen values are zero, but for counters that can't be kept from counting") {
    def zero(counting: Int*)(hex: String): Boolean =
      Try(FrozenCounters.zeroExcept(counting.toSet).validate(
        StubDriver.reporter(),
        Message.parse(Hex.from(s"C0 81 00 00 $hex")))).isSuccess
    zero()(frozen(0)) should equal(true)
    zero()(frozen(5)) should equal(false)
    zero(0)(frozen(5)) should equal(true)
    zero(0)(frozenAt1) should equal(false)
  }
}
