// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol.parsing

import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class UnsignedTest extends AnyFunSuite with Matchers {

  test("UInt8 serialization") {
    val bytes = UInt8(255).toBytes
    bytes should equal(List((~0).toByte))
  }

}
