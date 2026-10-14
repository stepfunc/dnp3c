// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._

object Group32 extends Group {
  def variations: List[Variation] = List(
    Group32Var0,
    Group32Var1,
    Group32Var2,
    Group32Var3,
    Group32Var4,
    Group32Var5,
    Group32Var6,
    Group32Var7,
    Group32Var8)

  def id: Byte = 32
}

import io.stepfunc.dnp3c.dnp3.app.Fields._

object Group32Var0 extends EmptyVariation(Group32, 0) with TypeTraits.EventVariationZero with TypeTraits.Level2

object Group32Var1 extends FixedSize(Group32, 1, List(flags, value32), AnalogBuilder) with TypeTraits.Event
    with TypeTraits.Level1

object Group32Var2 extends FixedSize(Group32, 2, List(flags, value16), AnalogBuilder) with TypeTraits.Event
    with TypeTraits.Level1

object Group32Var3 extends FixedSize(Group32, 3, List(flags, value32, time48), AnalogBuilder) with TypeTraits.Event
    with TypeTraits.Level4

object Group32Var4 extends FixedSize(Group32, 4, List(flags, value16, time48), AnalogBuilder) with TypeTraits.Event
    with TypeTraits.Level4

object Group32Var5 extends FixedSize(Group32, 5, List(flags, valueF32), AnalogBuilder) with TypeTraits.Event
    with TypeTraits.Level4

object Group32Var6 extends FixedSize(Group32, 6, List(flags, valueF64), AnalogBuilder) with TypeTraits.Event
    with TypeTraits.None

object Group32Var7 extends FixedSize(Group32, 7, List(flags, valueF32, time48), AnalogBuilder)
    with TypeTraits.Event with TypeTraits.Level4

object Group32Var8 extends FixedSize(Group32, 8, List(flags, valueF64, time48), AnalogBuilder)
    with TypeTraits.Event with TypeTraits.None
