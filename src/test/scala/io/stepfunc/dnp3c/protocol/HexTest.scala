// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.protocol

/**
 * Copyright 2011 John Adam Crain (jadamcrain@gmail.com)
 *
 * This file is the sole property of the copyright owner and is NOT
 * licensed to any 3rd parties.
 */

import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class HexTest extends AnyFunSuite with Matchers {

  val array = List(0x01.toByte, 0x02.toByte, 0xFF.toByte)

  test("fromHex") {
    Hex.from("01 02 FF") should equal(array)
    Hex.from("01 02 ff") should equal(array)
    Hex.from("0102 ff") should equal(array)
  }

}
