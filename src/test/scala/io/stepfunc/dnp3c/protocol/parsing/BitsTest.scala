// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol.parsing

import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class BitsTest extends AnyFunSuite with Matchers {

  test("produces correct list of values") {
    val bits = Bits(0xAA.toByte) // 0b10101010

    bits.right(1) should equal(List(false))

    bits.right(2) should equal(List(false, true))

    bits.right() should equal(List(false, true, false, true, false, true, false, true))
  }

}
