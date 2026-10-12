// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.link

import io.stepfunc.dnp3c.protocol.Hex
import io.stepfunc.dnp3c.protocol.parsing.{ UInt16LE, UInt8 }
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class LinkFrameTest extends AnyFunSuite with Matchers {

  test("Control byte generation") {
    val ctrl = Control(true, true, true, LinkFunction.CONFIRMED_USER_DATA)
    ctrl.toByte should equal(0xF3.toByte)
  }

  def testGeneration(expected: String)(gen: => List[Byte]) = Hex.convert(gen) should equal(expected)

  test("frame w/o data") {
    testGeneration("05 64 05 C0 01 00 00 04 E9 21") {
      val ctrl = Control(true, false, false, LinkFunction.RESET_LINK_STATES)
      LinkHeader(UInt8(5), ctrl, UInt16LE(1), UInt16LE(1024)).toBytes
    }
  }

  test("frame w/ data") {
    testGeneration("05 64 14 F3 01 00 00 04 0A 3B C0 C3 01 3C 02 06 3C 03 06 3C 04 06 3C 01 06 9A 12") {
      val ctrl = Control(true, true, true, LinkFunction.CONFIRMED_USER_DATA)
      val data = Hex.from("C0 C3 01 3C 02 06 3C 03 06 3C 04 06 3C 01 06")
      val hdr = LinkHeader(UInt8((5 + data.length).toShort), ctrl, UInt16LE(1), UInt16LE(1024))
      LinkFrame(hdr, data).toBytes
    }
  }

  def testBodySize(len: Int, expected: Int) =
    Crc.interlace(List.fill(len)(0xFF.toByte)).length should equal(expected)

  test("Body size 0")(testBodySize(0, 0))
  test("Body size 1")(testBodySize(1, 3))
  test("Body size 16")(testBodySize(16, 18))
  test("Body size 17")(testBodySize(17, 21))
  test("Body size 250")(testBodySize(250, 282))

}
