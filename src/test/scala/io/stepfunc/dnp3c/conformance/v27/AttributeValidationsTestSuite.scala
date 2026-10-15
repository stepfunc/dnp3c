// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api.options.AttributeOptions
import io.stepfunc.dnp3c.dnp3.{ DefaultIntegrationPlugin, MessageRecorder, ProcedureReporter, Requests }
import io.stepfunc.dnp3c.dnp3.Validate.passes
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group0, ReceivedAttribute }
import io.stepfunc.dnp3c.protocol.Hex
import io.stepfunc.dnp3c.protocol.logging.Logger
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class AttributeValidationsTestSuite extends AnyFunSuite with Matchers {

  import AttributeValidations._

  private val reporter = new ProcedureReporter(Logger.empty, new DefaultIntegrationPlugin())
  private val config = AttributeOptions.Config(List(240, 245), List(245))

  private def response(hex: String): Message = Message.parse(Hex.from("C0 81 00 00 " + hex))

  // g0v255 with qualifier 0x00 or 0x17 for set 0, listing 240 (read only) and 245 (read/write)
  private val listRange = "00 FF 00 00 00 FE 04 F0 00 F5 01 "
  private val listPrefixed = "00 FF 17 01 00 FE 04 F0 00 F5 01 "

  private def uint(variation: String, set: String = "00") = s"00 $variation 00 $set $set 02 01 05 "
  private def vstr(variation: String, set: String = "00") = s"00 $variation 00 $set $set 01 01 61 "

  test("Builds attribute requests") {
    Hex.convert(Requests.readAttribute(Sequence.one, 255).toBytes) should equal("C1 01 00 FF 00 00 00")
    Hex.convert(Requests.readAllObjects(Sequence.one, Group0(254)).toBytes) should equal("C1 01 00 FE 06")
    Hex.convert(Requests.writeAttribute(Sequence.two, 245, Group0.visibleString("ab")).toBytes) should
      equal("C2 02 00 F5 00 00 00 01 02 61 62")
  }

  test("The list of attribute variations must match the Device Profile") {
    val v = verifyAttributeList(config, onlyStandardSet = true)
    passes(v, listRange) should be(true)
    passes(v, listPrefixed) should be(true)
    passes(v, "00 FF 00 00 00 FE 02 F0 00") should be(false) // 245 missing
    passes(v, "00 FF 00 00 00 FE 04 F0 01 F5 01") should be(false) // 240 read/write
    passes(v, "00 FF 00 00 00 FE 06 F0 00 F5 01 F7 00") should be(false) // 247 unexpected
  }

  test("The list of attribute variations must be well formed") {
    val v = verifyAttributeList(config, onlyStandardSet = false)
    passes(v, "00 FF 00 00 00 FE 06 F0 00 F5 01 FE 00") should be(false) // lists 254
    passes(v, "00 FF 00 00 00 FE 06 F0 00 F5 01 F0 00") should be(false) // lists 240 twice
    passes(v, "00 FF 00 00 00 FE 04 F0 02 F5 01") should be(false) // properties 0x02
    passes(v, "00 FF 00 00 00 02 01 05") should be(false) // not a list
    passes(v, "00 FF 01 00 00 00 00 FE 04 F0 00 F5 01") should be(false) // qualifier 0x01
    passes(v, listRange + listRange) should be(false) // set 0 twice
    passes(v, "00 FF 00 01 01 FE 02 F0 00") should be(false) // no standard set
  }

  test("Other attribute sets are only checked when all sets are requested") {
    val otherSet = "00 FF 00 01 01 FE 02 F7 00 "
    passes(verifyAttributeList(config, onlyStandardSet = false), listRange + otherSet) should be(true)
    passes(verifyAttributeList(config, onlyStandardSet = true), listRange + otherSet) should be(false)
    passes(verifyAttributeList(config, onlyStandardSet = false), listRange + "00 FF 00 01 01 FE 02 FF 00") should
      be(false)
  }

  test("All attributes must be valid values of the Device Profile's variations") {
    val v = verifyAllAttributes(config, onlyStandardSet = true)
    passes(v, uint("F0") + vstr("F5")) should be(true)
    passes(v, uint("F0")) should be(false) // 245 missing
    passes(v, uint("F0") + vstr("F5") + vstr("F7")) should be(false) // 247 unexpected
    passes(v, uint("F0") + "00 F5 00 00 00 02 03 01 02 03") should be(false) // invalid UINT
    passes(v, uint("F0") + vstr("F5") + listRange) should be(false) // includes 255
    passes(v, uint("F0") + vstr("F5") + vstr("F5")) should be(false) // 245 twice
    passes(v, uint("F0") + vstr("F5") + "01 02 00 00 00 01") should be(false) // not group 0
    passes(v, uint("F0") + vstr("F5") + vstr("F7", "01")) should be(false) // set 1
    passes(verifyAllAttributes(config, onlyStandardSet = false), uint("F0") + vstr("F5") + vstr("F7", "01")) should
      be(true)
  }

  test("A single attribute must be the requested variation of the standard set") {
    passes(verifySingleAttribute(240), uint("F0")) should be(true)
    passes(verifySingleAttribute(240), vstr("F5")) should be(false)
    passes(verifySingleAttribute(240), uint("F0", "01")) should be(false)
    passes(verifySingleAttribute(240), uint("F0") + vstr("F5")) should be(false)
  }

  test("A single attribute must have the same value as the recorded all attributes response") {
    val recorder = new MessageRecorder
    recorder.record.validate(reporter, response(uint("F0") + vstr("F5")))
    passes(sameAsRecorded(recorder, 240), "00 F0 00 00 00 02 02 05 00") should be(true) // same value, wider encoding
    passes(sameAsRecorded(recorder, 240), "00 F0 00 00 00 02 01 06") should be(false)
    passes(sameAsRecorded(recorder, 246), uint("F6")) should be(false) // not recorded
  }

  test("Attribute validations apply across the fragments of a response") {
    val first = response(uint("F0"))
    val second = response(vstr("F5"))
    val v = verifyAllAttributes(config, onlyStandardSet = true)
    passes(v.acrossFragments, List(first, second)) should be(true)
    passes(v.toResponseValidation, List(first, second)) should be(false)
  }

  test("Accepts only the response qualifiers IEEE 1815 Table 14-5 allows for attributes") {
    val v = verifySingleAttribute(240)
    passes(v, "00 F0 17 01 00 02 01 05") should be(true) // 1-byte count and index
    passes(v, "00 F0 01 00 00 00 00 02 01 05") should be(false) // 2-byte start/stop
    passes(v, "00 F0 28 01 00 00 00 02 01 05") should be(false) // 2-byte count and index
    passes(verifyAllAttributes(config, onlyStandardSet = true), uint("F0") + "00 F5 28 01 00 00 00 01 01 61") should
      be(false)
  }

  test("Rejects an attribute set above 255") {
    an[Exception] should be thrownBy ReceivedAttribute.from(response("00 F0 01 00 01 00 01 02 01 05"))
  }

  test("Rejects a header carrying more than one attribute") {
    passes(verifySingleAttribute(240), "00 F0 00 00 01 02 01 05 02 01 05") should be(false)
  }

  test("Compares floating point values, including NaN") {
    val recorder = new MessageRecorder
    val nan = "00 F0 00 00 00 04 04 00 00 C0 7F"
    recorder.record.validate(reporter, response(nan))
    passes(sameAsRecorded(recorder, 240), nan) should be(true)
    passes(sameAsRecorded(recorder, 240), "00 F0 00 00 00 04 04 00 00 C0 3F") should be(false)
  }

  test("Rejects an attribute repeated in a later fragment") {
    val v = verifyAllAttributes(config, onlyStandardSet = true)
    val fragments = List(response(uint("F0") + vstr("F5")), response(vstr("F5")))
    passes(v.acrossFragments, fragments) should be(false)
  }
}
