// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api

import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class OptionParsingTestSuite extends AnyFunSuite with Matchers {

  test("parses single entry range") {
    Parse.indexList("42 ... 42") should equal(List(Index(42)))
  }

  test("parses valid list range") {
    Parse.indexList("0 ... 2") should equal(List(Index(0), Index(1), Index(2)))
  }

  test("throws if range runs backward") {
    assertThrows[Exception](Parse.indexList("3 ... 2"))
  }

  test("parses valid list") {
    Parse.indexList("1, 3, 7") should equal(List(Index(1), Index(3), Index(7)))
  }

  test("throws if garbage characters in list") {
    assertThrows[Exception](Parse.indexList("3, 4, ;7"))
  }

  test("parses a list of indices and ranges") {
    Parse.indexList("1, 3 ... 5, 9") should equal(List(1, 3, 4, 5, 9).map(Index.apply))
  }

  test("parses an empty list") {
    Parse.indexList("") should equal(Nil)
    Parse.indexList("  ") should equal(Nil)
  }

  test("throws on empty items") {
    assertThrows[Exception](Parse.indexList(","))
    assertThrows[Exception](Parse.indexList("240,"))
    assertThrows[Exception](Parse.indexList("240 ... 245 ..."))
  }

  test("writes a list default as a range only when it is contiguous") {
    def default(values: Int*): String =
      OptionTypes.indexList(options.TestOptions, "x", "", values.map(Index.apply).toList).defaultAsString
    default(0, 1, 2) should equal("0 ... 2")
    default(240, 245) should equal("240, 245")
    default(7) should equal("7")
    default() should equal("")
    OptionTypes.indexList(options.TestOptions, "x", "").defaultAsString should equal("0 ... 9")
  }

  test("requires a valid device attribute configuration") {
    import options.AttributeOptions._
    def config(supported: String, writable: String = "") = getConfig(Options.from(Map(
      supportedVariations.id -> supported,
      writableVariations.id -> writable)))

    config("240, 245 ... 247", "245") should equal(Config(List(240, 245, 246, 247), List(245)))
    assertThrows[Exception](config(""))
    assertThrows[Exception](config("0, 240"))
    assertThrows[Exception](config("240, 254"))
    assertThrows[Exception](config("240, 240"))
    assertThrows[Exception](config("240", "245"))
  }
}
