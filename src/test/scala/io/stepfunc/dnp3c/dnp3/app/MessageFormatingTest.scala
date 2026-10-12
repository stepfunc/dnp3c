// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import io.stepfunc.dnp3c.dnp3.Requests
import io.stepfunc.dnp3c.dnp3.app.Fields._
import io.stepfunc.dnp3c.dnp3.app.objects._
import io.stepfunc.dnp3c.protocol.Hex
import io.stepfunc.dnp3c.protocol.parsing.{ Field, UInt16LE, UInt32LE, UInt8 }
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class MessageFormatingTest extends AnyFunSuite with Matchers {

  test("Integrity poll generation") {
    val integrity = "C0 01 3C 01 06" // read class 0
    val message = Message(Control.request(Sequence.zero), AppFunction.read, None, List(AllObjectsHeader(Group60Var1)))
    Hex.convert(message.toBytes) should equal(integrity)
    val parsed = Message.parse(Hex.from(integrity))
    parsed should equal(message)
  }

  test("Read all events") {
    val eventScan = "C0 01 3C 02 06 3C 03 06 3C 04 06" // read class 1,2,3
    val headers = List(AllObjectsHeader(Group60Var2), AllObjectsHeader(Group60Var3), AllObjectsHeader(Group60Var4))
    val message = Message(Control.request(Sequence.zero), AppFunction.read, None, headers)
    Hex.convert(message.toBytes) should equal(eventScan)
    val parsed = Message.parse(Hex.from(eventScan))
    parsed should equal(message)
  }

  test("Read simple response w/ IIN") {
    val rsp = "C0 81 00 00"
    val message = Message(Control.request(Sequence.zero), AppFunction.response, Some(IIN.empty), Nil)
    Hex.convert(message.toBytes) should equal(rsp)
    val parsed = Message.parse(Hex.from(rsp))
    parsed should equal(message)
  }

  test("Read simple response with Group1Var2") {
    val rsp = "C0 81 00 00 01 02 00 00 00 01"
    val payload = FieldHeaderPayload(FieldHeaderPayloadObject(
      Field.collect(flags(0x01.toByte)),
      BinaryPoint(false, Some(0x01), None, PointType.Static)))
    val headers = OneByteStartStopHeader(Group1Var2, UInt8(0), UInt8(0), payload)
    val message = Message(Control.request(Sequence.zero), AppFunction.response, Some(IIN.empty), List(headers))
    Hex.convert(message.toBytes) should equal(rsp)
    val parsed = Message.parse(Hex.from(rsp))
    parsed should equal(message)
  }

  test("Read 2 byte count header w/ 1 elements") {
    val rsp = "C0 81 00 00 01 02 08 01 00 FF"
    val payload = FieldHeaderPayload(FieldHeaderPayloadObject(
      Field.collect(flags(0xFF.toByte)),
      BinaryPoint(true, Some(0xFF.toByte), None, PointType.Static)))
    val headers = TwoByteCountHeader(Group1Var2, UInt16LE(1), payload)
    val message = Message(Control.request(Sequence.zero), AppFunction.response, Some(IIN.empty), List(headers))
    Hex.convert(message.toBytes) should equal(rsp)
    val parsed = Message.parse(Hex.from(rsp))
    parsed should equal(message)
  }

  test("Read Group1 Var1") {
    val rsp = "C0 81 00 00 01 01 07 01 FF"
    val emitted = "C0 81 00 00 01 01 07 01 01"
    val payload = BitFieldHeaderPayload(List(true), false)
    val headers = OneByteCountHeader(Group1Var1, UInt8(1), payload)
    val message = Message(Control.request(Sequence.zero), AppFunction.response, Some(IIN.empty), List(headers))
    Hex.convert(message.toBytes) should equal(emitted)
    val parsed = Message.parse(Hex.from(rsp))
    parsed should equal(message)
  }

  test("Write Group80 (clear restart IIN") {
    val request = Requests.clearRestartIIN(Sequence.three)
    val bytes = request.toBytes
    Hex.convert(bytes) should equal("C3 02 50 01 00 07 07 00")
    Message.parse(bytes) should equal(request)
  }

  test("g12v1 control codes are decoded into their parts") {
    ControlCode(0x41.toByte).opType should equal(OpType.PULSE_ON)
    ControlCode(0x41.toByte).tripClose should equal(TripCloseCode.CLOSE)
    ControlCode(0x13.toByte).queue should be(true)
    ControlCode(0x13.toByte).clear should be(false)
    ControlCode(0x23.toByte).queue should be(false)
    ControlCode(0x23.toByte).clear should be(true)
    ControlCode(0xB3.toByte).name should equal("LATCH_ON, TRIP, QUEUE, CLEAR")
    ControlCode(0xCF.toByte).name should equal("Unknown, RESERVED")
    Fields.code(0x81.toByte).description should equal("code: 0x81 (PULSE_ON, TRIP)")
  }

  test("Command status codes are named, and unknown codes don't throw") {
    CommandStatus.get(4.toByte) should equal(CommandStatus.NOT_SUPPORTED)
    CommandStatus.get(200.toByte) should equal(CommandStatus.unknown(200.toByte))
    Fields.status(8.toByte).description should equal("status: 8 (TOO_MANY_OPS)")
    Fields.status(200.toByte).description should equal("status: 200 (Unknown)")
  }

  test("g12v1 with an unknown status round trips") {
    val hex = "C0 81 00 00 0C 01 17 01 00 41 01 64 00 00 00 C8 00 00 00 C8"
    val crob = Group12Var1(ControlCode.pulseClose, 1, UInt32LE(100), UInt32LE(200), CommandStatus.unknown(200.toByte))
    val header = OneByteCountAndIndexHeader(
      Group12Var1,
      UInt8(1),
      PrefixedPayload.applyU8(List(Prefix(UInt8(0), crob.toFixedSizeObject))))
    val message = Message(Control.request(Sequence.zero), AppFunction.response, Some(IIN.empty), List(header))
    Hex.convert(message.toBytes) should equal(hex)
    Message.parse(Hex.from(hex)) should equal(message)
  }
}
