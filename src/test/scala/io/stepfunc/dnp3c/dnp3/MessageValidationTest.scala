// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3

import io.stepfunc.dnp3c.dnp3.app._
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class MessageValidationTest extends AnyFunSuite with Matchers {

  private val reporter = StubDriver.reporter()

  private def response(seq: Sequence): Message =
    Message(Control.request(seq), AppFunction.response, Some(IIN.empty), Nil)

  test("a check that passes defers nothing") {
    var checked = List.empty[Message]
    val validation = MessageValidation.check("check")(m => checked = checked :+ m)

    validation.validate(reporter, response(Sequence.zero)) should equal(Nil)
    checked should equal(List(response(Sequence.zero)))
  }

  test("a check that throws fails the validation") {
    val validation = MessageValidation.check("check")(_ => throw new Exception("bad"))
    an[Exception] should be thrownBy validation.validate(reporter, response(Sequence.zero))
  }

  test("a check runs on each fragment of a response") {
    var checked = List.empty[Sequence]
    val validation = MessageValidation.check("check")(m => checked = checked :+ m.ctrl.seq)

    validation.toResponseValidation.validate(reporter, List(response(Sequence.zero), response(Sequence.one))) should
      equal(Nil)
    checked should equal(List(Sequence.zero, Sequence.one))
  }
}
