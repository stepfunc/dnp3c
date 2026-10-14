// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._

object Group23 extends Group {
  def variations: List[Variation] = List(
    Group23Var0,
    Group23Var1,
    Group23Var2,
    Group23Var3,
    Group23Var4,
    Group23Var5,
    Group23Var6,
    Group23Var7,
    Group23Var8)

  def id: Byte = 23
}

import io.stepfunc.dnp3c.dnp3.app.Fields._

object Group23Var0 extends EmptyVariation(Group23, 0) with TypeTraits.EventVariationZero with TypeTraits.Level3

object Group23Var1 extends FixedSize(Group23, 1, List(flags, count32), FrozenCounterBuilder) with TypeTraits.Event
    with TypeTraits.Level3

object Group23Var2 extends FixedSize(Group23, 2, List(flags, count16), FrozenCounterBuilder) with TypeTraits.Event
    with TypeTraits.Level3

object Group23Var3 extends FixedSize(Group23, 3, List(flags, count32), FrozenCounterBuilder) with TypeTraits.Event
    with TypeTraits.None

object Group23Var4 extends FixedSize(Group23, 4, List(flags, count16), FrozenCounterBuilder) with TypeTraits.Event
    with TypeTraits.None

object Group23Var5 extends FixedSize(Group23, 5, List(flags, count32, time48), FrozenCounterBuilder)
    with TypeTraits.Event with TypeTraits.Level4

object Group23Var6 extends FixedSize(Group23, 6, List(flags, count16, time48), FrozenCounterBuilder)
    with TypeTraits.Event with TypeTraits.Level4

object Group23Var7 extends FixedSize(Group23, 7, List(flags, count32, time48), FrozenCounterBuilder)
    with TypeTraits.Event with TypeTraits.None

object Group23Var8 extends FixedSize(Group23, 8, List(flags, count16, time48), FrozenCounterBuilder)
    with TypeTraits.Event with TypeTraits.None
