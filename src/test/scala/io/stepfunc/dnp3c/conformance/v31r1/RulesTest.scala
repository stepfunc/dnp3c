// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.Revisions
import io.stepfunc.dnp3c.dnp3.app.Message
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class RulesTest extends AnyFunSuite with Matchers {

  private def breaks(hex: String): Boolean =
    Rules.confirmOnlyWhenRequired.violation(Message.parse(Hex.from(hex))).isDefined

  // the application control octet: FIR, FIN, CON and UNS are 0x80, 0x40, 0x20 and 0x10
  private val staticData = "0A 02 00 00 00 01" // g10v2, index 0, online
  private val events = "02 01 17 01 00 01" // g2v1, one event at index 0, online

  test("a final solicited fragment without events mustn't ask for confirmation") {
    breaks(s"E0 81 00 00 $staticData") should equal(true)
    breaks(s"C0 81 00 00 $staticData") should equal(false)
    breaks("E0 81 00 00") should equal(true) // a null response
  }

  test("a response with events, a fragment another follows, and an unsolicited response may ask for confirmation") {
    breaks(s"E0 81 00 00 $events") should equal(false)
    breaks(s"A0 81 00 00 $staticData") should equal(false)
    breaks("F0 82 00 00") should equal(false)
    breaks("E0 81 01 00") should equal(false) // IIN1.0: a broadcast to acknowledge
  }

  private def unsolicitedBreaks(hex: String): Boolean =
    Rules.unsolicitedOnlyEvents.violation(Message.parse(Hex.from(hex))).isDefined

  test("an unsolicited response carries only events, and the CTOs that time them") {
    unsolicitedBreaks(s"F0 82 00 00 $events") should equal(false)
    unsolicitedBreaks("F0 82 00 00 33 01 07 01 00 00 00 00 00 00 02 03 17 01 00 01 00 00") should equal(false)
    unsolicitedBreaks("F0 82 00 00") should equal(false) // a null response
    unsolicitedBreaks(s"F0 82 00 00 $staticData") should equal(true)
    unsolicitedBreaks(s"F0 82 00 00 $events $staticData") should equal(true)
    unsolicitedBreaks(s"C0 81 00 00 $staticData") should equal(false) // solicited
  }

  test("3.1r1 applies the rules, and 2.7 has none") {
    Revisions.v3_1r1.policies should contain(Rules.unsolicitedOnlyEvents)
    Revisions.v3_1r1.policies should contain(Rules.confirmOnlyWhenRequired)
    Revisions.v2_7.policies should equal(Nil)
  }
}
