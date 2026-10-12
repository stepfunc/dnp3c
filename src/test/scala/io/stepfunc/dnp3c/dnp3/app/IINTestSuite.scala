// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class IINTestSuite extends AnyFunSuite with Matchers {

  test("Complement of empty is all bits") {
    ~IIN.empty should equal(IIN(0xFF.toByte, 0xFF.toByte))
  }

  test("Subtract of all bits is empty") {
    (~IIN.empty) - (~IIN.empty) should equal(IIN.empty)
  }

  test("Subtract of a single bits works properly") {
    val sub = (IIN.BROADCAST | IIN.FUNC_NOT_SUPPORTED) - IIN.BROADCAST
    sub should equal(IIN.FUNC_NOT_SUPPORTED)
  }

}
