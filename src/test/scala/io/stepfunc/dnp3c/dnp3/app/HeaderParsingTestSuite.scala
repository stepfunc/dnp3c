// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import io.stepfunc.dnp3c.dnp3.app.objects.{ Group2Var1, Group3Var1 }
import io.stepfunc.dnp3c.protocol.Hex
import io.stepfunc.dnp3c.protocol.parsing.UInt8
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class HeaderParsingTestSuite extends AnyFunSuite with Matchers {

  test("Can parse 8 objects of g3v1") {
    val headers = Message.parseObjectHeaders(Hex.from("03 01 00 00 07 FF FF"))

    val expected = List(OneByteStartStopHeader(
      Group3Var1,
      UInt8(0),
      UInt8(7),
      DoubleBitFieldHeaderPayload(
        List.fill(8)(DoubleBit.Indeterminate)
      )
    ))

    headers should equal(expected)
  }

  test("Can parse 4 objects of g3v1") {
    val headers = Message.parseObjectHeaders(Hex.from("03 01 00 00 03 01"))

    val expected = List(OneByteStartStopHeader(
      Group3Var1,
      UInt8(0),
      UInt8(3),
      DoubleBitFieldHeaderPayload(
        DoubleBit.DeterminedOff :: List.fill(3)(DoubleBit.Intermediate)
      )
    ))

    headers should equal(expected)
  }

  test("One-byte start/stop treats indices >= 0x80 as unsigned") {
    val headers = Message.parseObjectHeaders(Hex.from("03 01 00 7F 80 00"))

    val expected = List(OneByteStartStopHeader(
      Group3Var1,
      UInt8(0x7F),
      UInt8(0x80),
      DoubleBitFieldHeaderPayload(
        List.fill(2)(DoubleBit.Intermediate)
      )
    ))

    headers should equal(expected)
  }

  test("One-byte count treats counts >= 0x80 as unsigned") {
    val headers = Message.parseObjectHeaders(Hex.from("03 01 07 80 " + "00 " * 32))

    val expected = List(OneByteCountHeader(
      Group3Var1,
      UInt8(0x80),
      DoubleBitFieldHeaderPayload(
        List.fill(128)(DoubleBit.Intermediate)
      )
    ))

    headers should equal(expected)
  }

  test("One-byte count and index treats indices >= 0x80 as unsigned") {
    val headers = Message.parseObjectHeaders(Hex.from("02 01 17 01 FF 01"))

    headers match {
      case List(OneByteCountAndIndexHeader(Group2Var1, count, PrefixedPayload.U8(List(obj)))) =>
        count should equal(UInt8(1))
        obj.index should equal(UInt8(0xFF))
      case _ => fail("unexpected headers: " + headers)
    }
  }

}
