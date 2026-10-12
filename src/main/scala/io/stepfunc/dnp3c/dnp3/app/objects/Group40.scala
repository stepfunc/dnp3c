// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.protocol.parsing.Field

object Group40 extends Group {
  def variations: List[Variation] = List(Group40Var0, Group40Var1, Group40Var2, Group40Var3, Group40Var4)

  def id: Byte = 40
}

import io.stepfunc.dnp3c.dnp3.app.Fields._

object Group40Var0 extends EmptyVariation(Group40, 0) with TypeTraits.StaticVariationZero with TypeTraits.Level1
object Group40Var1 extends FixedSize(Group40, 1, List(flags, value32), StaticAnalogOutputStatusBuilder)
    with TypeTraits.Static with TypeTraits.Level3 {
  def apply(flags: Byte, value: Int): Field.Collection = {
    Field.collect(Fields.flags(flags), Fields.value32(value))
  }
}
object Group40Var2 extends FixedSize(Group40, 2, List(flags, value16), StaticAnalogOutputStatusBuilder)
    with TypeTraits.Static with TypeTraits.Level1 {
  def apply(flags: Byte, value: Short): Field.Collection = {
    Field.collect(Fields.flags(flags), Fields.value16(value))
  }
}
object Group40Var3 extends FixedSize(Group40, 3, List(flags, valueF32), StaticAnalogOutputStatusBuilder)
    with TypeTraits.Static with TypeTraits.Level4
object Group40Var4 extends FixedSize(Group40, 4, List(flags, valueF64), StaticAnalogOutputStatusBuilder)
    with TypeTraits.Static with TypeTraits.None
