// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3

import io.stepfunc.dnp3c.dnp3.app.objects._
import io.stepfunc.dnp3c.dnp3.app.{ AllObjectsHeader, OneByteStartStopHeader, _ }
import io.stepfunc.dnp3c.protocol.parsing.{ Field, UInt16LE, UInt48LE, UInt8 }

object Requests {

  def readClass0(seq: Sequence): Message = readAllObjects(seq, Group60Var1)

  def readClass1(seq: Sequence): Message = readAllObjects(seq, Group60Var2)

  def readClass2(seq: Sequence): Message = readAllObjects(seq, Group60Var3)

  def readClass3(seq: Sequence): Message = readAllObjects(seq, Group60Var4)

  def readClass123(seq: Sequence): Message = readAllObjects(seq, Group60Var2, Group60Var3, Group60Var4)

  def readClass1230(seq: Sequence): Message = readAllObjects(seq, Group60Var2, Group60Var3, Group60Var4, Group60Var1)

  def clearRestartIIN(seq: Sequence): Message = {
    Message.request(
      seq,
      AppFunction.write,
      List(OneByteStartStopHeader(Group80Var1, UInt8(7), UInt8(7), BitFieldHeaderPayload(List(false), false))))
  }

  def confirm(seq: Sequence, uns: Unsolicited) =
    Message(Control(First.Yes, Final.Yes, Confirm.No, uns, seq), AppFunction.confirm, None, Nil)

  def allObjects(function: AppFunction, seq: Sequence, gv: Variation*): Message =
    Message.request(seq, function, gv.map(AllObjectsHeader.apply).toList)

  def readAllObjects(seq: Sequence, gv: Variation*): Message = allObjects(AppFunction.read, seq, gv*)

  // a READ for count objects of a variation: qualifier 0x08 (a two-byte count) if twoByte, otherwise 0x07
  def readCount(seq: Sequence, variation: Variation, count: Int, twoByte: Boolean): Message = {
    val header =
      if (twoByte) TwoByteCountHeader(variation, UInt16LE(count), FieldHeaderPayload.empty)
      else OneByteCountHeader(variation, UInt8(count), FieldHeaderPayload.empty)
    readHeaders(seq, header)
  }

  def readHeaders(seq: Sequence, headers: ObjectHeader*): Message = readHeaders(seq, headers.toList)

  def readHeaders(seq: Sequence, headers: List[ObjectHeader]): Message = Message.request(seq, AppFunction.read, headers)

  // read a standard set (index 0) device attribute
  def readAttribute(seq: Sequence, variation: Int): Message =
    readHeaders(seq, OneByteStartStopHeader(Group0(variation), UInt8(0), UInt8(0), FieldHeaderPayload.empty))

  // write a standard set (index 0) device attribute
  def writeAttribute(seq: Sequence, variation: Int, value: Field.Collection): Message = Message.request(
    seq,
    AppFunction.write,
    List(OneByteStartStopHeader(
      Group0(variation),
      UInt8(0),
      UInt8(0),
      FieldHeaderPayload(FieldHeaderPayloadObject(value))))
  )

  def writeTime(seq: Sequence, time: UInt48LE): Message = {
    Message.request(
      seq,
      AppFunction.write,
      List(
        OneByteCountHeader(
          Group50Var1,
          UInt8(1),
          FieldHeaderPayload(
            FieldHeaderPayloadObject(
              Field.Collection(List(Field.u48("time", time)))
            )
          )
        )
      )
    )
  }

}
