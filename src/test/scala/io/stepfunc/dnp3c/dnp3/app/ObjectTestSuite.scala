// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

import io.stepfunc.dnp3c.dnp3.app.objects._
import io.stepfunc.dnp3c.protocol.Hex
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

@RunWith(classOf[JUnitRunner])
class ObjectTestSuite extends AnyFunSuite with Matchers {

  test("Binary Inputs Static (group 1)") {
    Group1Var1.parse(3, Hex.from("05")).value.iterate should contain theSameElementsInOrderAs List(
      BinaryPoint(true, None, None, PointType.Static),
      BinaryPoint(false, None, None, PointType.Static),
      BinaryPoint(true, None, None, PointType.Static)
    )

    Group1Var2.parse(1, Hex.from("81")).value.iterate should contain theSameElementsInOrderAs List(
      BinaryPoint(true, Some(0x81.toByte), None, PointType.Static)
    )
  }

  test("Binary Inputs Event (group 2)") {
    Group2Var1.parse(1, Hex.from("81")).value.iterate should contain theSameElementsInOrderAs List(
      BinaryPoint(true, Some(0x81.toByte), None, PointType.Event)
    )

    Group2Var2.parse(1, Hex.from("81 10 00 00 00 00 00")).value.iterate should contain theSameElementsInOrderAs List(
      BinaryPoint(true, Some(0x81.toByte), Some(Timestamp(0x10)), PointType.Event)
    )

    Group2Var3.parse(1, Hex.from("81 10 00")).value.iterate should contain theSameElementsInOrderAs List(
      BinaryPoint(true, Some(0x81.toByte), Some(Timestamp(0x10, true)), PointType.Event)
    )
  }

  test("Double-Bit Binary Inputs Static (group 3)") {
    Group3Var1.parse(4, Hex.from("9C")).value.iterate should contain theSameElementsInOrderAs List(
      DoubleBitPoint(DoubleBit.Intermediate, None, None, PointType.Static),
      DoubleBitPoint(DoubleBit.Indeterminate, None, None, PointType.Static),
      DoubleBitPoint(DoubleBit.DeterminedOff, None, None, PointType.Static),
      DoubleBitPoint(DoubleBit.DeterminedOn, None, None, PointType.Static)
    )

    Group3Var2.parse(2, Hex.from("41 81")).value.iterate should contain theSameElementsInOrderAs List(
      DoubleBitPoint(DoubleBit.DeterminedOff, Some(0x41.toByte), None, PointType.Static),
      DoubleBitPoint(DoubleBit.DeterminedOn, Some(0x81.toByte), None, PointType.Static)
    )
  }

  test("Double-Bit Binary Inputs Event (group 4)") {
    Group4Var1.parse(1, Hex.from("41")).value.iterate should contain theSameElementsInOrderAs List(
      DoubleBitPoint(DoubleBit.DeterminedOff, Some(0x41.toByte), None, PointType.Event)
    )

    Group4Var2.parse(1, Hex.from("41 10 00 00 00 00 00")).value.iterate should contain theSameElementsInOrderAs List(
      DoubleBitPoint(DoubleBit.DeterminedOff, Some(0x41.toByte), Some(Timestamp(0x10)), PointType.Event)
    )

    Group4Var3.parse(1, Hex.from("41 10 00")).value.iterate should contain theSameElementsInOrderAs List(
      DoubleBitPoint(DoubleBit.DeterminedOff, Some(0x41.toByte), Some(Timestamp(0x10, true)), PointType.Event)
    )
  }

  test("Binary Outputs Static (group 10)") {
    Group10Var1.parse(3, Hex.from("05")).value.iterate should contain theSameElementsInOrderAs List(
      BinaryOutputStatusPoint(true, None, None, PointType.Static),
      BinaryOutputStatusPoint(false, None, None, PointType.Static),
      BinaryOutputStatusPoint(true, None, None, PointType.Static)
    )

    Group10Var2.parse(1, Hex.from("81")).value.iterate should contain theSameElementsInOrderAs List(
      BinaryOutputStatusPoint(true, Some(0x81.toByte), None, PointType.Static)
    )
  }

  test("Binary Outputs Event (group 11)") {
    Group11Var1.parse(1, Hex.from("81")).value.iterate should contain theSameElementsInOrderAs List(
      BinaryOutputStatusPoint(true, Some(0x81.toByte), None, PointType.Event)
    )

    Group11Var2.parse(1, Hex.from("81 10 00 00 00 00 00")).value.iterate should contain theSameElementsInOrderAs List(
      BinaryOutputStatusPoint(true, Some(0x81.toByte), Some(Timestamp(0x10)), PointType.Event)
    )
  }

  test("Counters Static (group 20)") {
    Group20Var1.parse(1, Hex.from("01 FF 00 00 00")).value.iterate should contain theSameElementsInOrderAs List(
      CounterPoint(0xFF, false, Some(0x01), None, PointType.Static)
    )

    Group20Var2.parse(1, Hex.from("01 FF 00")).value.iterate should contain theSameElementsInOrderAs List(
      CounterPoint(0xFF, false, Some(0x01), None, PointType.Static)
    )

    Group20Var3.parse(1, Hex.from("01 FF 00 00 00")).value.iterate should contain theSameElementsInOrderAs List(
      CounterPoint(0xFF, false, Some(0x01), None, PointType.Static)
    )

    Group20Var4.parse(1, Hex.from("01 FF 00")).value.iterate should contain theSameElementsInOrderAs List(
      CounterPoint(0xFF, false, Some(0x01), None, PointType.Static)
    )

    Group20Var5.parse(1, Hex.from("FF 00 00 00")).value.iterate should contain theSameElementsInOrderAs List(
      CounterPoint(0xFF, false, None, None, PointType.Static)
    )

    Group20Var6.parse(1, Hex.from("FF 00")).value.iterate should contain theSameElementsInOrderAs List(
      CounterPoint(0xFF, false, None, None, PointType.Static)
    )

    Group20Var7.parse(1, Hex.from("FF 00 00 00")).value.iterate should contain theSameElementsInOrderAs List(
      CounterPoint(0xFF, false, None, None, PointType.Static)
    )

    Group20Var8.parse(1, Hex.from("FF 00")).value.iterate should contain theSameElementsInOrderAs List(
      CounterPoint(0xFF, false, None, None, PointType.Static)
    )
  }

  test("Frozen Counters Static (group 21)") {
    Group21Var1.parse(1, Hex.from("01 FF 00 00 00")).value.iterate should contain theSameElementsInOrderAs List(
      CounterPoint(0xFF, true, Some(0x01), None, PointType.Static)
    )

    Group21Var2.parse(1, Hex.from("01 FF 00")).value.iterate should contain theSameElementsInOrderAs List(
      CounterPoint(0xFF, true, Some(0x01), None, PointType.Static)
    )

    Group21Var3.parse(1, Hex.from("01 FF 00 00 00")).value.iterate should contain theSameElementsInOrderAs List(
      CounterPoint(0xFF, true, Some(0x01), None, PointType.Static)
    )

    Group21Var4.parse(1, Hex.from("01 FF 00")).value.iterate should contain theSameElementsInOrderAs List(
      CounterPoint(0xFF, true, Some(0x01), None, PointType.Static)
    )

    Group21Var5.parse(1, Hex.from("01 FF 00 00 00 10 00 00 00 00 00")).value.iterate should
      contain theSameElementsInOrderAs List(
        CounterPoint(0xFF, true, Some(0x01), Some(Timestamp(0x10)), PointType.Static)
      )

    Group21Var6.parse(1, Hex.from("01 FF 00 10 00 00 00 00 00")).value.iterate should contain theSameElementsInOrderAs
      List(
        CounterPoint(0xFF, true, Some(0x01), Some(Timestamp(0x10)), PointType.Static)
      )

    Group21Var7.parse(1, Hex.from("01 FF 00 00 00 10 00 00 00 00 00")).value.iterate should
      contain theSameElementsInOrderAs List(
        CounterPoint(0xFF, true, Some(0x01), Some(Timestamp(0x10)), PointType.Static)
      )

    Group21Var8.parse(1, Hex.from("01 FF 00 10 00 00 00 00 00")).value.iterate should contain theSameElementsInOrderAs
      List(
        CounterPoint(0xFF, true, Some(0x01), Some(Timestamp(0x10)), PointType.Static)
      )

    Group21Var9.parse(1, Hex.from("FF 00 00 00")).value.iterate should contain theSameElementsInOrderAs List(
      CounterPoint(0xFF, true, None, None, PointType.Static)
    )

    Group21Var10.parse(1, Hex.from("FF 00")).value.iterate should contain theSameElementsInOrderAs List(
      CounterPoint(0xFF, true, None, None, PointType.Static)
    )

    Group21Var11.parse(1, Hex.from("FF 00 00 00")).value.iterate should contain theSameElementsInOrderAs List(
      CounterPoint(0xFF, true, None, None, PointType.Static)
    )

    Group21Var12.parse(1, Hex.from("FF 00")).value.iterate should contain theSameElementsInOrderAs List(
      CounterPoint(0xFF, true, None, None, PointType.Static)
    )
  }

  test("Counters Event (group 22)") {
    Group22Var1.parse(1, Hex.from("01 FF 00 00 00")).value.iterate should contain theSameElementsInOrderAs List(
      CounterPoint(0xFF, false, Some(0x01), None, PointType.Event)
    )

    Group22Var2.parse(1, Hex.from("01 FF 00")).value.iterate should contain theSameElementsInOrderAs List(
      CounterPoint(0xFF, false, Some(0x01), None, PointType.Event)
    )

    Group22Var3.parse(1, Hex.from("01 FF 00 00 00")).value.iterate should contain theSameElementsInOrderAs List(
      CounterPoint(0xFF, false, Some(0x01), None, PointType.Event)
    )

    Group22Var4.parse(1, Hex.from("01 FF 00")).value.iterate should contain theSameElementsInOrderAs List(
      CounterPoint(0xFF, false, Some(0x01), None, PointType.Event)
    )

    Group22Var5.parse(1, Hex.from("01 FF 00 00 00 10 00 00 00 00 00")).value.iterate should
      contain theSameElementsInOrderAs List(
        CounterPoint(0xFF, false, Some(0x01), Some(Timestamp(0x10)), PointType.Event)
      )

    Group22Var6.parse(1, Hex.from("01 FF 00 10 00 00 00 00 00")).value.iterate should contain theSameElementsInOrderAs
      List(
        CounterPoint(0xFF, false, Some(0x01), Some(Timestamp(0x10)), PointType.Event)
      )

    Group22Var7.parse(1, Hex.from("01 FF 00 00 00 10 00 00 00 00 00")).value.iterate should
      contain theSameElementsInOrderAs List(
        CounterPoint(0xFF, false, Some(0x01), Some(Timestamp(0x10)), PointType.Event)
      )

    Group22Var8.parse(1, Hex.from("01 FF 00 10 00 00 00 00 00")).value.iterate should contain theSameElementsInOrderAs
      List(
        CounterPoint(0xFF, false, Some(0x01), Some(Timestamp(0x10)), PointType.Event)
      )
  }

  test("Frozen Counters Event (group 23)") {
    Group23Var1.parse(1, Hex.from("01 FF 00 00 00")).value.iterate should contain theSameElementsInOrderAs List(
      CounterPoint(0xFF, true, Some(0x01), None, PointType.Event)
    )

    Group23Var2.parse(1, Hex.from("01 FF 00")).value.iterate should contain theSameElementsInOrderAs List(
      CounterPoint(0xFF, true, Some(0x01), None, PointType.Event)
    )

    Group23Var3.parse(1, Hex.from("01 FF 00 00 00")).value.iterate should contain theSameElementsInOrderAs List(
      CounterPoint(0xFF, true, Some(0x01), None, PointType.Event)
    )

    Group23Var4.parse(1, Hex.from("01 FF 00")).value.iterate should contain theSameElementsInOrderAs List(
      CounterPoint(0xFF, true, Some(0x01), None, PointType.Event)
    )

    Group23Var5.parse(1, Hex.from("01 FF 00 00 00 10 00 00 00 00 00")).value.iterate should
      contain theSameElementsInOrderAs List(
        CounterPoint(0xFF, true, Some(0x01), Some(Timestamp(0x10)), PointType.Event)
      )

    Group23Var6.parse(1, Hex.from("01 FF 00 10 00 00 00 00 00")).value.iterate should contain theSameElementsInOrderAs
      List(
        CounterPoint(0xFF, true, Some(0x01), Some(Timestamp(0x10)), PointType.Event)
      )

    Group23Var7.parse(1, Hex.from("01 FF 00 00 00 10 00 00 00 00 00")).value.iterate should
      contain theSameElementsInOrderAs List(
        CounterPoint(0xFF, true, Some(0x01), Some(Timestamp(0x10)), PointType.Event)
      )

    Group23Var8.parse(1, Hex.from("01 FF 00 10 00 00 00 00 00")).value.iterate should contain theSameElementsInOrderAs
      List(
        CounterPoint(0xFF, true, Some(0x01), Some(Timestamp(0x10)), PointType.Event)
      )
  }

  test("Analogs Static (group 30)") {
    Group30Var1.parse(1, Hex.from("01 FF 00 00 00")).value.iterate should contain theSameElementsInOrderAs List(
      AnalogPoint(0xFF, false, Some(0x01), None, PointType.Static)
    )

    Group30Var2.parse(1, Hex.from("01 FF 00")).value.iterate should contain theSameElementsInOrderAs List(
      AnalogPoint(0xFF, false, Some(0x01), None, PointType.Static)
    )

    Group30Var3.parse(1, Hex.from("FF 00 00 00")).value.iterate should contain theSameElementsInOrderAs List(
      AnalogPoint(0xFF, false, None, None, PointType.Static)
    )

    Group30Var4.parse(1, Hex.from("FF 00")).value.iterate should contain theSameElementsInOrderAs List(
      AnalogPoint(0xFF, false, None, None, PointType.Static)
    )

    Group30Var5.parse(1, Hex.from("01 00 00 7F 43")).value.iterate should contain theSameElementsInOrderAs List(
      AnalogPoint(0xFF, false, Some(0x01), None, PointType.Static)
    )

    Group30Var6.parse(1, Hex.from("01 00 00 00 00 00 E0 6F 40")).value.iterate should contain theSameElementsInOrderAs
      List(
        AnalogPoint(0xFF, false, Some(0x01), None, PointType.Static)
      )
  }

  test("Frozen Analogs Static (group 31)") {
    Group31Var1.parse(1, Hex.from("01 FF 00 00 00")).value.iterate should contain theSameElementsInOrderAs List(
      AnalogPoint(0xFF, true, Some(0x01), None, PointType.Static)
    )

    Group31Var2.parse(1, Hex.from("01 FF 00")).value.iterate should contain theSameElementsInOrderAs List(
      AnalogPoint(0xFF, true, Some(0x01), None, PointType.Static)
    )

    Group31Var3.parse(1, Hex.from("01 FF 00 00 00 10 00 00 00 00 00")).value.iterate should
      contain theSameElementsInOrderAs List(
        AnalogPoint(0xFF, true, Some(0x01), Some(Timestamp(0x10)), PointType.Static)
      )

    Group31Var4.parse(1, Hex.from("01 FF 00 10 00 00 00 00 00")).value.iterate should contain theSameElementsInOrderAs
      List(
        AnalogPoint(0xFF, true, Some(0x01), Some(Timestamp(0x10)), PointType.Static)
      )

    Group31Var5.parse(1, Hex.from("FF 00 00 00")).value.iterate should contain theSameElementsInOrderAs List(
      AnalogPoint(0xFF, true, None, None, PointType.Static)
    )

    Group31Var6.parse(1, Hex.from("FF 00")).value.iterate should contain theSameElementsInOrderAs List(
      AnalogPoint(0xFF, true, None, None, PointType.Static)
    )

    Group31Var7.parse(1, Hex.from("01 00 00 7F 43")).value.iterate should contain theSameElementsInOrderAs List(
      AnalogPoint(0xFF, true, Some(0x01), None, PointType.Static)
    )

    Group31Var8.parse(1, Hex.from("01 00 00 00 00 00 E0 6F 40")).value.iterate should contain theSameElementsInOrderAs
      List(
        AnalogPoint(0xFF, true, Some(0x01), None, PointType.Static)
      )
  }

  test("Analogs Static (group 32)") {
    Group32Var1.parse(1, Hex.from("01 FF 00 00 00")).value.iterate should contain theSameElementsInOrderAs List(
      AnalogPoint(0xFF, false, Some(0x01), None, PointType.Event)
    )

    Group32Var2.parse(1, Hex.from("01 FF 00")).value.iterate should contain theSameElementsInOrderAs List(
      AnalogPoint(0xFF, false, Some(0x01), None, PointType.Event)
    )

    Group32Var3.parse(1, Hex.from("01 FF 00 00 00 10 00 00 00 00 00")).value.iterate should
      contain theSameElementsInOrderAs List(
        AnalogPoint(0xFF, false, Some(0x01), Some(Timestamp(0x10)), PointType.Event)
      )

    Group32Var4.parse(1, Hex.from("01 FF 00 10 00 00 00 00 00")).value.iterate should contain theSameElementsInOrderAs
      List(
        AnalogPoint(0xFF, false, Some(0x01), Some(Timestamp(0x10)), PointType.Event)
      )

    Group32Var5.parse(1, Hex.from("01 00 00 7F 43")).value.iterate should contain theSameElementsInOrderAs List(
      AnalogPoint(0xFF, false, Some(0x01), None, PointType.Event)
    )

    Group32Var6.parse(1, Hex.from("01 00 00 00 00 00 E0 6F 40")).value.iterate should contain theSameElementsInOrderAs
      List(
        AnalogPoint(0xFF, false, Some(0x01), None, PointType.Event)
      )

    Group32Var7.parse(1, Hex.from("01 00 00 7F 43 10 00 00 00 00 00")).value.iterate should
      contain theSameElementsInOrderAs List(
        AnalogPoint(0xFF, false, Some(0x01), Some(Timestamp(0x10)), PointType.Event)
      )

    Group32Var8.parse(1, Hex.from("01 00 00 00 00 00 E0 6F 40 10 00 00 00 00 00")).value.iterate should
      contain theSameElementsInOrderAs List(
        AnalogPoint(0xFF, false, Some(0x01), Some(Timestamp(0x10)), PointType.Event)
      )
  }

  test("Frozen Analogs Static (group 33)") {
    Group33Var1.parse(1, Hex.from("01 FF 00 00 00")).value.iterate should contain theSameElementsInOrderAs List(
      AnalogPoint(0xFF, true, Some(0x01), None, PointType.Event)
    )

    Group33Var2.parse(1, Hex.from("01 FF 00")).value.iterate should contain theSameElementsInOrderAs List(
      AnalogPoint(0xFF, true, Some(0x01), None, PointType.Event)
    )

    Group33Var3.parse(1, Hex.from("01 FF 00 00 00 10 00 00 00 00 00")).value.iterate should
      contain theSameElementsInOrderAs List(
        AnalogPoint(0xFF, true, Some(0x01), Some(Timestamp(0x10)), PointType.Event)
      )

    Group33Var4.parse(1, Hex.from("01 FF 00 10 00 00 00 00 00")).value.iterate should contain theSameElementsInOrderAs
      List(
        AnalogPoint(0xFF, true, Some(0x01), Some(Timestamp(0x10)), PointType.Event)
      )

    Group33Var5.parse(1, Hex.from("01 00 00 7F 43")).value.iterate should contain theSameElementsInOrderAs List(
      AnalogPoint(0xFF, true, Some(0x01), None, PointType.Event)
    )

    Group33Var6.parse(1, Hex.from("01 00 00 00 00 00 E0 6F 40")).value.iterate should contain theSameElementsInOrderAs
      List(
        AnalogPoint(0xFF, true, Some(0x01), None, PointType.Event)
      )

    Group33Var7.parse(1, Hex.from("01 00 00 7F 43 10 00 00 00 00 00")).value.iterate should
      contain theSameElementsInOrderAs List(
        AnalogPoint(0xFF, true, Some(0x01), Some(Timestamp(0x10)), PointType.Event)
      )

    Group33Var8.parse(1, Hex.from("01 00 00 00 00 00 E0 6F 40 10 00 00 00 00 00")).value.iterate should
      contain theSameElementsInOrderAs List(
        AnalogPoint(0xFF, true, Some(0x01), Some(Timestamp(0x10)), PointType.Event)
      )
  }

  test("Analog OutputStatus Static (group 40)") {
    Group40Var1.parse(1, Hex.from("01 FF 00 00 00")).value.iterate should contain theSameElementsInOrderAs List(
      AnalogOutputStatusPoint(0xFF, Option(0x01), None, PointType.Static)
    )

    Group40Var2.parse(1, Hex.from("01 FF 00")).value.iterate should contain theSameElementsInOrderAs List(
      AnalogOutputStatusPoint(0xFF, Some(0x01), None, PointType.Static)
    )

    Group40Var3.parse(1, Hex.from("01 00 00 7F 43")).value.iterate should contain theSameElementsInOrderAs List(
      AnalogOutputStatusPoint(0xFF, Some(0x01), None, PointType.Static)
    )

    Group40Var4.parse(1, Hex.from("01 00 00 00 00 00 E0 6F 40")).value.iterate should contain theSameElementsInOrderAs
      List(
        AnalogOutputStatusPoint(0xFF, Some(0x01), None, PointType.Static)
      )
  }

  test("Analog Output Status Event (group 42)") {
    Group42Var1.parse(1, Hex.from("01 FF 00 00 00")).value.iterate should contain theSameElementsInOrderAs List(
      AnalogOutputStatusPoint(0xFF, Some(0x01), None, PointType.Event)
    )

    Group42Var2.parse(1, Hex.from("01 FF 00")).value.iterate should contain theSameElementsInOrderAs List(
      AnalogOutputStatusPoint(0xFF, Some(0x01), None, PointType.Event)
    )

    Group42Var3.parse(1, Hex.from("01 FF 00 00 00 10 00 00 00 00 00")).value.iterate should
      contain theSameElementsInOrderAs List(
        AnalogOutputStatusPoint(0xFF, Some(0x01), Some(Timestamp(0x10)), PointType.Event)
      )

    Group42Var4.parse(1, Hex.from("01 FF 00 10 00 00 00 00 00")).value.iterate should contain theSameElementsInOrderAs
      List(
        AnalogOutputStatusPoint(0xFF, Some(0x01), Some(Timestamp(0x10)), PointType.Event)
      )

    Group42Var5.parse(1, Hex.from("01 00 00 7F 43")).value.iterate should contain theSameElementsInOrderAs List(
      AnalogOutputStatusPoint(0xFF, Some(0x01), None, PointType.Event)
    )

    Group42Var6.parse(1, Hex.from("01 00 00 00 00 00 E0 6F 40")).value.iterate should contain theSameElementsInOrderAs
      List(
        AnalogOutputStatusPoint(0xFF, Some(0x01), None, PointType.Event)
      )

    Group42Var7.parse(1, Hex.from("01 00 00 7F 43 10 00 00 00 00 00")).value.iterate should
      contain theSameElementsInOrderAs List(
        AnalogOutputStatusPoint(0xFF, Some(0x01), Some(Timestamp(0x10)), PointType.Event)
      )

    Group42Var8.parse(1, Hex.from("01 00 00 00 00 00 E0 6F 40 10 00 00 00 00 00")).value.iterate should
      contain theSameElementsInOrderAs List(
        AnalogOutputStatusPoint(0xFF, Some(0x01), Some(Timestamp(0x10)), PointType.Event)
      )
  }
}
