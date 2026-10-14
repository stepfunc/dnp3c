// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.common

import io.stepfunc.dnp3c.dnp3.{ DefaultIntegrationPlugin, MessageValidation, ProcedureReporter }
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects._
import io.stepfunc.dnp3c.protocol.Hex
import io.stepfunc.dnp3c.protocol.logging.Logger
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class ValidationsTestSuite extends AnyFunSuite with Matchers {

  private val reporter = new ProcedureReporter(Logger.empty, new DefaultIntegrationPlugin())

  private def response(iin: IIN, headers: List[ObjectHeader] = Nil): Message =
    Message(Control.request(Sequence.zero), AppFunction.response, Some(iin), headers)

  private def passes(validation: MessageValidation, message: Message): Boolean =
    scala.util.Try(validation.validate(reporter, message)).isSuccess

  test("generated events: at least one, all of the allowed variations") {
    def events(objects: String) = Message.parse(Hex.from("C0 81 00 00 " + objects))
    val g2v1 = Validations.verifyEvents(Set(Group2Var1))
    passes(g2v1, events("02 01 17 01 05 81")) should be(true)
    passes(g2v1, events("")) should be(false)
    passes(g2v1, events("02 01 17 00")) should be(false) // a header with no events
    passes(g2v1, events("02 02 17 01 05 81 00 00 00 00 00 00")) should be(false) // g2v2
  }

  test("Null Response accepts an empty response with no IIN bits") {
    passes(Validations.isNull, response(IIN.empty)) should be(true)
  }

  test("Null Response accepts non-error IIN bits") {
    passes(Validations.isNull, response(IIN.DEVICE_RESTART | IIN.NEED_TIME)) should be(true)
  }

  test("Null Response rejects each error IIN bit") {
    List(IIN.FUNC_NOT_SUPPORTED, IIN.OBJECT_UNKNOWN, IIN.PARAM_ERROR).foreach { bit =>
      passes(Validations.isNull, response(bit)) should be(false)
    }
  }

  test("Null Response rejects objects") {
    passes(Validations.isNull, response(IIN.empty, List(AllObjectsHeader(Group60Var1)))) should be(false)
  }

  test("Null-or-Error Response accepts an Error Response with data, but only the allowed error bits") {
    val v = Validations.isNullOrErrorResponse(IIN.OBJECT_UNKNOWN | IIN.PARAM_ERROR)
    val data = List(AllObjectsHeader(Group60Var1))
    passes(v, response(IIN.empty)) should be(true)
    passes(v, response(IIN.empty, data)) should be(false)
    passes(v, response(IIN.OBJECT_UNKNOWN, data)) should be(true)
    passes(v, response(IIN.OBJECT_UNKNOWN | IIN.PARAM_ERROR)) should be(true)
    passes(v, response(IIN.FUNC_NOT_SUPPORTED)) should be(false)
  }

  test("Command echo requires an exact match and no error IIN bits") {
    val header = AllObjectsHeader(Group60Var1)
    val v = Validations.echoes(header)
    passes(v, response(IIN.DEVICE_RESTART, List(header))) should be(true)
    passes(v, response(IIN.PARAM_ERROR, List(header))) should be(false)
    passes(v, response(IIN.empty)) should be(false)
  }

  test("Table 8-3/8-4 checks accept double-bit objects only when the DUT supports them") {
    // g3v2 index 0 (start/stop 0x00) and g4v1 index 0 (count and index 0x17), both with flags 0x01
    val static = Message.parseObjectHeaders(Hex.from("03 02 00 00 00 01"))
    val event = Message.parseObjectHeaders(Hex.from("04 01 17 01 00 01"))

    passes(Validations.verifyOnlyTable83Contents(Validations.Tables(2, false)), response(IIN.empty, static)) should
      be(false)
    passes(Validations.verifyOnlyTable83Contents(Validations.Tables(2, true)), response(IIN.empty, static)) should
      be(true)
    passes(Validations.verifyOnlyTable84Contents(Validations.Tables(2, false)), response(IIN.empty, event)) should
      be(false)
    passes(Validations.verifyOnlyTable84Contents(Validations.Tables(2, true)), response(IIN.empty, event)) should
      be(true)
    passes(
      Validations.verifyOnlyTable83OrTable84Contents(Validations.Tables(2, false)),
      response(IIN.empty, static ++ event)) should be(false)
    passes(
      Validations.verifyOnlyTable83OrTable84Contents(Validations.Tables(2, true)),
      response(IIN.empty, static ++ event)) should be(true)
  }

  test("Tables 8-3 and 8-4 allow the objects of the DUT's subset level and the levels below") {
    // g21v1 (level 2) and g40v1 (level 3) at index 0, start/stop 0x00, and g23v1 (level 3) at index 0, 0x17
    val frozenCounter = Message.parseObjectHeaders(Hex.from("15 01 00 00 00 01 00 00 00 00"))
    val outputStatus = Message.parseObjectHeaders(Hex.from("28 01 00 00 00 01 00 00 00 00"))
    val frozenCounterEvent = Message.parseObjectHeaders(Hex.from("17 01 17 01 00 01 00 00 00 00"))
    def table83(level: Int) = Validations.verifyOnlyTable83Contents(Validations.Tables(level, doubleBit = false))
    def table84(level: Int) = Validations.verifyOnlyTable84Contents(Validations.Tables(level, doubleBit = false))

    // g1v2, index 0, start/stop 0x00: IEEE 1815 puts it at level 2, but table 8-3 allows it at level 1
    val binaryWithFlags = Message.parseObjectHeaders(Hex.from("01 02 00 00 00 01"))
    passes(table83(1), response(IIN.empty, binaryWithFlags)) should be(true)
    passes(table83(1), response(IIN.empty, frozenCounter)) should be(false)
    passes(table83(2), response(IIN.empty, frozenCounter)) should be(true)
    passes(table83(2), response(IIN.empty, outputStatus)) should be(false)
    passes(table83(3), response(IIN.empty, outputStatus)) should be(true)
    passes(table84(2), response(IIN.empty, frozenCounterEvent)) should be(false)
    passes(table84(3), response(IIN.empty, frozenCounterEvent)) should be(true)
  }

  test("Binary input event responses accept a CTO and require it before relative time events") {
    val v = new InputEventSteps(InputType.BinaryInput).verifyValidGroupVariationAndQualifiers
    val cto = "33 01 07 01 00 00 00 00 00 00 " // g51v1, count 1
    val relative = "02 03 17 01 00 01 00 00 " // g2v3, index 0, flags 0x01, relative time 0
    val withTime = "02 02 17 01 00 01 00 00 00 00 00 00 " // g2v2, index 0, flags 0x01, time 0

    def check(hex: String): Boolean = passes(v, response(IIN.empty, Message.parseObjectHeaders(Hex.from(hex))))

    check(cto + relative) should be(true)
    check(withTime) should be(true)
    check(relative) should be(false)
    check(relative + cto) should be(false)
  }

  test("Table 8-4 check requires a CTO before relative time events") {
    val v = Validations.verifyOnlyTable84Contents(Validations.Tables(2, false))
    val cto = "33 01 07 01 00 00 00 00 00 00 " // g51v1, count 1
    val relative = "02 03 17 01 00 01 00 00 " // g2v3, index 0, flags 0x01, relative time 0

    def check(hex: String): Boolean = passes(v, response(IIN.empty, Message.parseObjectHeaders(Hex.from(hex))))

    check(cto + relative) should be(true)
    check(relative) should be(false)
    check(relative + cto) should be(false)
  }

  test("Note 5 flags allow the state bit for binary output status") {
    def check(hex: String): Boolean =
      passes(Validations.verifyFlagsComplyWithFlagNote, response(IIN.empty, Message.parseObjectHeaders(Hex.from(hex))))

    check("0A 02 00 00 00 81") should be(true) // g10v2 index 0: online, state on
    check("0A 02 00 00 00 03") should be(false) // g10v2 index 0: online, restart
  }

  test("First fragment check requires FIR") {
    val first = response(IIN.empty)
    val second = first.copy(ctrl = first.ctrl.copy(fir = First.No))

    passes(Validations.isFirstFragment, first) should be(true)
    passes(Validations.isFirstFragment, second) should be(false)
  }

  test("acrossFragments validates once over the objects of every fragment") {
    var objectCounts = List.empty[Int]
    val counting = new MessageValidation {
      override def description = Nil
      override def validate(reporter: ProcedureReporter, message: Message) = {
        objectCounts = message.headers.size :: objectCounts
        Nil
      }
    }
    val fragment = response(IIN.empty, List(AllObjectsHeader(Group60Var1)))

    counting.toResponseValidation.validate(reporter, List(fragment, fragment))
    objectCounts should equal(List(1, 1))

    objectCounts = Nil
    counting.acrossFragments.validate(reporter, List(fragment, fragment))
    objectCounts should equal(List(2))
  }

  test("containingAnyOf accepts one or both bits but not neither") {
    val v = Validations.containingAnyOf(IIN.OBJECT_UNKNOWN | IIN.PARAM_ERROR)
    passes(v, response(IIN.OBJECT_UNKNOWN)) should be(true)
    passes(v, response(IIN.PARAM_ERROR)) should be(true)
    passes(v, response(IIN.OBJECT_UNKNOWN | IIN.PARAM_ERROR)) should be(true)
    passes(v, response(IIN.empty)) should be(false)
  }

  // a response with the given object headers, as bytes
  private def parsed(objects: String): Message = Message.parse(Hex.from("C0 81 00 00 " + objects))

  private val cto = "33 01 07 01 00 00 00 00 00 00" // g51v1, count 1
  private val ctoTwoByteCount = "33 01 08 01 00 00 00 00 00 00 00"

  test("a single event is one event object, or a CTO and one relative time event") {
    val v = Validations.singleEvent("binary input event", Set(Group2Var1, Group2Var2), afterCto = Set(Group2Var3))
    passes(v, parsed("02 01 17 01 05 81")) should be(true) // g2v1, 1-byte index
    passes(v, parsed("02 01 28 01 00 05 00 81")) should be(true) // g2v1, 2-byte index
    passes(v, parsed(s"$cto 02 03 17 01 05 81 00 00")) should be(true)
    passes(v, parsed(s"$cto 02 03 28 01 00 05 00 81 00 00")) should be(true)

    passes(v, parsed("")) should be(false)
    passes(v, parsed("02 01 17 02 05 81 06 81")) should be(false) // two events
    passes(v, parsed("02 03 17 01 05 81 00 00")) should be(false) // relative time without a CTO
    passes(v, parsed(s"$cto 02 01 17 01 05 81")) should be(false) // a CTO before an event that doesn't use it
    passes(v, parsed("01 02 00 05 05 81")) should be(false) // a static object
    // Table 8-4 lists qualifier 0x07 for a CTO, not 0x08
    passes(v, parsed(s"$ctoTwoByteCount 02 03 28 01 00 05 00 81 00 00")) should be(false)
  }

  test("tables 8-5 and 8-6 accept only their own counter group, in start/stop ranges") {
    val counter = "14 01 00 05 05 01 2A 00 00 00" // g20v1, qualifier 0x00, index 5
    val frozen = "15 01 00 05 05 01 2A 00 00 00" // g21v1
    passes(Validations.verifyOnlyTable85Contents, parsed(counter)) should be(true)
    passes(Validations.verifyOnlyTable85Contents, parsed(frozen)) should be(false)
    passes(Validations.verifyOnlyTable86Contents, parsed(frozen)) should be(true)
    passes(Validations.verifyOnlyTable86Contents, parsed(counter)) should be(false)
    passes(Validations.verifyOnlyTable85Contents, parsed("16 01 17 01 05 01 2A 00 00 00")) should be(false) // an event
  }

  test("a delay measurement response is one g52v2 with count 1") {
    passes(Validations.isDelayMeasurement, parsed("34 02 07 01 E8 03")) should be(true)
    passes(Validations.isDelayMeasurement, parsed("34 02 07 02 E8 03 E8 03")) should be(false)
    passes(Validations.isDelayMeasurement, parsed("34 01 07 01 01 00")) should be(false) // g52v1
    passes(Validations.isDelayMeasurement, parsed("")) should be(false)
  }
}
