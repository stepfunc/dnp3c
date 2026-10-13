// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3

import io.stepfunc.dnp3c.dnp3.app.{ AppFunction, Control, IIN, Message, Sequence }
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class PoliciesTest extends AnyFunSuite with Matchers {

  // a rule every message breaks
  private val never = new MessagePolicy {
    def id = "never"
    def description = "no message at all"
    def violation(message: Message): Option[String] = Some("it's a message")
  }

  private val message = Message(Control.request(Sequence.zero), AppFunction.response, Some(IIN.empty), Nil)

  test("a message that breaks a rule fails, naming the rule") {
    val ex = intercept[Exception](new Policies(List(never)).check(message))
    ex.getMessage should include("never (no message at all): it's a message")
  }

  test("an exempt rule isn't checked, and only while the exemption runs") {
    val policies = new Policies(List(never))
    policies.exempting(Set("never"))(policies.check(message))
    an[Exception] should be thrownBy policies.check(message)
  }

  test("no rules, no failures") {
    Policies.none.check(message)
  }
}
