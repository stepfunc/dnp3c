// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._

object Group20 extends Group {
  def variations: List[Variation] = List(
    Group20Var0,
    Group20Var1,
    Group20Var2,
    Group20Var3,
    Group20Var4,
    Group20Var5,
    Group20Var6,
    Group20Var7,
    Group20Var8)

  def id: Byte = 20
}

import io.stepfunc.dnp3c.dnp3.app.Fields._

object Group20Var0 extends EmptyVariation(Group20, 0) with TypeTraits.StaticVariationZero with TypeTraits.Level2

object Group20Var1 extends FixedSize(Group20, 1, List(flags, count32), StaticCounterBuilder) with TypeTraits.Static
    with TypeTraits.Level1

object Group20Var2 extends FixedSize(Group20, 2, List(flags, count16), StaticCounterBuilder) with TypeTraits.Static
    with TypeTraits.Level1

object Group20Var3 extends FixedSize(Group20, 3, List(flags, count32), StaticCounterBuilder) with TypeTraits.Static
    with TypeTraits.None

object Group20Var4 extends FixedSize(Group20, 4, List(flags, count16), StaticCounterBuilder) with TypeTraits.Static
    with TypeTraits.None

object Group20Var5 extends FixedSize(Group20, 5, List(count32), StaticCounterBuilder) with TypeTraits.Static
    with TypeTraits.Level1

object Group20Var6 extends FixedSize(Group20, 6, List(count16), StaticCounterBuilder) with TypeTraits.Static
    with TypeTraits.Level1

object Group20Var7 extends FixedSize(Group20, 7, List(count32), StaticCounterBuilder) with TypeTraits.Static
    with TypeTraits.None

object Group20Var8 extends FixedSize(Group20, 8, List(count16), StaticCounterBuilder) with TypeTraits.Static
    with TypeTraits.None
