// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.protocol.parsing.Field

object Group41 extends Group {
  def variations: List[Variation] = List(Group41Var0, Group41Var1, Group41Var2, Group41Var3, Group41Var4)

  def id: Byte = 41
}

import io.stepfunc.dnp3c.dnp3.app.Fields._

object Group41Var0 extends EmptyVariation(Group41, 0) with TypeTraits.Command with TypeTraits.Level4
object Group41Var1 extends FixedSize(Group41, 1, List(value32, status)) with TypeTraits.Command with TypeTraits.Level3
object Group41Var2 extends FixedSize(Group41, 2, List(value16, status)) with TypeTraits.Command with TypeTraits.Level1
object Group41Var3 extends FixedSize(Group41, 3, List(valueF32, status)) with TypeTraits.Command with TypeTraits.Level4
object Group41Var4 extends FixedSize(Group41, 4, List(valueF64, status)) with TypeTraits.Command with TypeTraits.None

case class Group41Var2(value: Short, status: CommandStatus) extends FixedSizeSerializable {
  override val variation: Variation = Group41Var2
  override def toFixedSizeObject: Field.Collection = Field.collect(
    value16(value),
    Fields.status(status.value)
  )
}
