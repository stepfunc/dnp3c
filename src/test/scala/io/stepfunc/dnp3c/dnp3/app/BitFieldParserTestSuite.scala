// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class BitFieldParserTestSuite extends AnyFunSuite with Matchers {

  test("Can extract single bit") {
    val input = List(0x01.toByte, 0xFF.toByte)
    val result = BitFieldParser(1).parse(input)
    result.value should equal(List(true))
    result.remainder should equal(input.tail)
  }

  test("Can extract multiple bits spanning a boundary") {
    val input = List(0xFF.toByte, 0x01.toByte, 0xFF.toByte)
    val result = BitFieldParser(10).parse(input)
    result.value should equal(List.fill(9)(true) ::: List(false))
    result.remainder should equal(input.drop(2))
  }

  test("Can extract multiple double-bits spanning a boundary") {
    val input = List(0xAA.toByte, 0xAA.toByte, 0x0A.toByte)
    val result = DoubleBitFieldParser(10).parse(input)
    result.value should equal(List.fill(10)(DoubleBit.DeterminedOn))
  }

}
