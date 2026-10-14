// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._

object Group21 extends Group {
  def variations: List[Variation] = List(
    Group21Var0,
    Group21Var1,
    Group21Var2,
    Group21Var3,
    Group21Var4,
    Group21Var5,
    Group21Var6,
    Group21Var7,
    Group21Var8,
    Group21Var9,
    Group21Var10,
    Group21Var11,
    Group21Var12
  )

  def id: Byte = 21
}

import io.stepfunc.dnp3c.dnp3.app.Fields._

object Group21Var0 extends EmptyVariation(Group21, 0) with TypeTraits.StaticVariationZero with TypeTraits.Level2

object Group21Var1 extends FixedSize(Group21, 1, List(flags, count32), FrozenCounterBuilder)
    with TypeTraits.Static with TypeTraits.Level2

object Group21Var2 extends FixedSize(Group21, 2, List(flags, count16), FrozenCounterBuilder)
    with TypeTraits.Static with TypeTraits.Level2

object Group21Var3 extends FixedSize(Group21, 3, List(flags, count32), FrozenCounterBuilder)
    with TypeTraits.Static with TypeTraits.None

object Group21Var4 extends FixedSize(Group21, 4, List(flags, count16), FrozenCounterBuilder)
    with TypeTraits.Static with TypeTraits.None

object Group21Var5 extends FixedSize(Group21, 5, List(flags, count32, time48), FrozenCounterBuilder)
    with TypeTraits.Static with TypeTraits.Level4

object Group21Var6 extends FixedSize(Group21, 6, List(flags, count16, time48), FrozenCounterBuilder)
    with TypeTraits.Static with TypeTraits.Level4

object Group21Var7 extends FixedSize(Group21, 7, List(flags, count32, time48), FrozenCounterBuilder)
    with TypeTraits.Static with TypeTraits.None

object Group21Var8 extends FixedSize(Group21, 8, List(flags, count16, time48), FrozenCounterBuilder)
    with TypeTraits.Static with TypeTraits.None

object Group21Var9 extends FixedSize(Group21, 9, List(count32), FrozenCounterBuilder) with TypeTraits.Static
    with TypeTraits.Level2

object Group21Var10 extends FixedSize(Group21, 10, List(count16), FrozenCounterBuilder) with TypeTraits.Static
    with TypeTraits.Level2

object Group21Var11 extends FixedSize(Group21, 11, List(count32), FrozenCounterBuilder) with TypeTraits.Static
    with TypeTraits.None

object Group21Var12 extends FixedSize(Group21, 12, List(count16), FrozenCounterBuilder) with TypeTraits.Static
    with TypeTraits.None
