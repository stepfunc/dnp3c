// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._

object Group31 extends Group {
  def variations: List[Variation] =
    List(Group31Var1, Group31Var2, Group31Var3, Group31Var4, Group31Var5, Group31Var6, Group31Var7, Group31Var8)
  def id: Byte = 31
}

import io.stepfunc.dnp3c.dnp3.app.Fields._

object Group31Var1 extends FixedSize(Group31, 1, List(flags, value32), StaticFrozenAnalogBuilder) with TypeTraits.Static
    with TypeTraits.None

object Group31Var2 extends FixedSize(Group31, 2, List(flags, value16), StaticFrozenAnalogBuilder) with TypeTraits.Static
    with TypeTraits.None

object Group31Var3 extends FixedSize(Group31, 3, List(flags, value32, time48), StaticFrozenAnalogBuilder)
    with TypeTraits.Static with TypeTraits.None

object Group31Var4 extends FixedSize(Group31, 4, List(flags, value16, time48), StaticFrozenAnalogBuilder)
    with TypeTraits.Static with TypeTraits.None

object Group31Var5 extends FixedSize(Group31, 5, List(value32), StaticFrozenAnalogBuilder) with TypeTraits.Static
    with TypeTraits.None

object Group31Var6 extends FixedSize(Group31, 6, List(value16), StaticFrozenAnalogBuilder) with TypeTraits.Static
    with TypeTraits.None

object Group31Var7 extends FixedSize(Group31, 7, List(flags, valueF32), StaticFrozenAnalogBuilder)
    with TypeTraits.Static with TypeTraits.None

object Group31Var8 extends FixedSize(Group31, 8, List(flags, valueF64), StaticFrozenAnalogBuilder)
    with TypeTraits.Static with TypeTraits.None
