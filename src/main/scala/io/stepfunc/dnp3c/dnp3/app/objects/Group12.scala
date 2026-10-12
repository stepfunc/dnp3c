// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.protocol.parsing.{ Field, UInt32LE }

object Group12 extends Group {
  def variations: List[Variation] = List(Group12Var1)

  def id: Byte = 12

  val crobFields = List(Fields.code, Fields.count8, Fields.onTime, Fields.offTime, Fields.status)
}

case class Group12Var1(code: ControlCode, count: Byte, onTime: UInt32LE, offTime: UInt32LE, status: CommandStatus)
    extends FixedSizeSerializable {

  override val variation: Variation = Group12Var1
  override def toFixedSizeObject: Field.Collection = Field.collect(
    Fields.code(code.value),
    Fields.count8(count),
    Fields.onTime(onTime),
    Fields.offTime(offTime),
    Fields.status(status.value)
  )
}

object Group12Var1 extends FixedSize(Group12, 1, Group12.crobFields) with TypeTraits.Command with TypeTraits.Level1
