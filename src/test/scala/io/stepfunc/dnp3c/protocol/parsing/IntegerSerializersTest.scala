// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol.parsing

import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class IntegerSerializersTest extends AnyFunSuite with Matchers {

  test("Short serializer LE") {
    UInt16LE(258).toBytes should equal(List(0x02.toByte, 0x01.toByte))
  }

  test("Byte Serializer LE") {
    UInt8(0xAA).toBytes should equal(List(0xAA.toByte))
  }

  test("assembly of multiple types") {

    Hex.convert(UInt8(0xFF) ++ UInt16LE(0xABCD) ++ UInt32LE(0x01234567)) should equal("FF CD AB 67 45 23 01")

  }

}
