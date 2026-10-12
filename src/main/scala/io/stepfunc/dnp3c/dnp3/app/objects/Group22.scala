// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._

object Group22 extends Group {
  def variations: List[Variation] = List(
    Group22Var0,
    Group22Var1,
    Group22Var2,
    Group22Var3,
    Group22Var4,
    Group22Var5,
    Group22Var6,
    Group22Var7,
    Group22Var8)

  def id: Byte = 22
}

import io.stepfunc.dnp3c.dnp3.app.Fields._

object Group22Var0 extends EmptyVariation(Group22, 0) with TypeTraits.EventVariationZero with TypeTraits.Level2

object Group22Var1 extends FixedSize(Group22, 1, List(flags, count32), EventCounterBuilder) with TypeTraits.Event
    with TypeTraits.Level1

object Group22Var2 extends FixedSize(Group22, 2, List(flags, count16), EventCounterBuilder) with TypeTraits.Event
    with TypeTraits.Level1

object Group22Var3 extends FixedSize(Group22, 3, List(flags, count32), EventCounterBuilder) with TypeTraits.Event
    with TypeTraits.None

object Group22Var4 extends FixedSize(Group22, 4, List(flags, count16), EventCounterBuilder) with TypeTraits.Event
    with TypeTraits.None

object Group22Var5 extends FixedSize(Group22, 5, List(flags, count32, time48), EventCounterBuilder)
    with TypeTraits.Event with TypeTraits.None

object Group22Var6 extends FixedSize(Group22, 6, List(flags, count16, time48), EventCounterBuilder)
    with TypeTraits.Event with TypeTraits.None

object Group22Var7 extends FixedSize(Group22, 7, List(flags, count32, time48), EventCounterBuilder)
    with TypeTraits.Event with TypeTraits.None

object Group22Var8 extends FixedSize(Group22, 8, List(flags, count16, time48), EventCounterBuilder)
    with TypeTraits.Event with TypeTraits.None
