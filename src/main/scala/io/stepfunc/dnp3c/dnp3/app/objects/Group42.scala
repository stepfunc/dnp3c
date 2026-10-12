// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._

object Group42 extends Group {
  def variations: List[Variation] = List(
    Group42Var0,
    Group42Var1,
    Group42Var2,
    Group42Var3,
    Group42Var4,
    Group42Var5,
    Group42Var6,
    Group42Var7,
    Group42Var8)

  def id: Byte = 42
}

import io.stepfunc.dnp3c.dnp3.app.Fields._

object Group42Var0 extends EmptyVariation(Group42, 0) with TypeTraits.EventVariationZero with TypeTraits.Level4

object Group42Var1 extends FixedSize(Group42, 1, List(flags, value32), EventAnalogOutputStatusBuilder)
    with TypeTraits.Event with TypeTraits.Level4

object Group42Var2 extends FixedSize(Group42, 2, List(flags, value16), EventAnalogOutputStatusBuilder)
    with TypeTraits.Event with TypeTraits.Level4

object Group42Var3 extends FixedSize(Group42, 3, List(flags, value32, time48), EventAnalogOutputStatusBuilder)
    with TypeTraits.Event with TypeTraits.Level4

object Group42Var4 extends FixedSize(Group42, 4, List(flags, value16, time48), EventAnalogOutputStatusBuilder)
    with TypeTraits.Event with TypeTraits.Level4

object Group42Var5 extends FixedSize(Group42, 5, List(flags, valueF32), EventAnalogOutputStatusBuilder)
    with TypeTraits.Event with TypeTraits.Level4

object Group42Var6 extends FixedSize(Group42, 6, List(flags, valueF64), EventAnalogOutputStatusBuilder)
    with TypeTraits.Event with TypeTraits.None

object Group42Var7 extends FixedSize(Group42, 7, List(flags, valueF32, time48), EventAnalogOutputStatusBuilder)
    with TypeTraits.Event with TypeTraits.Level4

object Group42Var8 extends FixedSize(Group42, 8, List(flags, valueF64, time48), EventAnalogOutputStatusBuilder)
    with TypeTraits.Event with TypeTraits.None
