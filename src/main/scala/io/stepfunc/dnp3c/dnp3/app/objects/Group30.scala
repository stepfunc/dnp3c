// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._

object Group30 extends Group {
  def variations: List[Variation] =
    List(Group30Var0, Group30Var1, Group30Var2, Group30Var3, Group30Var4, Group30Var5, Group30Var6)

  def id: Byte = 30
}

import io.stepfunc.dnp3c.dnp3.app.Fields._

object Group30Var0 extends EmptyVariation(Group30, 0) with TypeTraits.StaticVariationZero with TypeTraits.Level2

object Group30Var1 extends FixedSize(Group30, 1, List(flags, value32), StaticAnalogBuilder) with TypeTraits.Static
    with TypeTraits.Level1

object Group30Var2 extends FixedSize(Group30, 2, List(flags, value16), StaticAnalogBuilder) with TypeTraits.Static
    with TypeTraits.Level1

object Group30Var3 extends FixedSize(Group30, 3, List(value32), StaticAnalogBuilder) with TypeTraits.Static
    with TypeTraits.Level1

object Group30Var4 extends FixedSize(Group30, 4, List(value16), StaticAnalogBuilder) with TypeTraits.Static
    with TypeTraits.Level1

object Group30Var5 extends FixedSize(Group30, 5, List(flags, valueF32), StaticAnalogBuilder) with TypeTraits.Static
    with TypeTraits.Level4

object Group30Var6 extends FixedSize(Group30, 6, List(flags, valueF64), StaticAnalogBuilder) with TypeTraits.Static
    with TypeTraits.None
