// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._

object Group43 extends Group {
  def variations: List[Variation] = List(
    Group43Var0,
    Group43Var1,
    Group43Var2,
    Group43Var3,
    Group43Var4,
    Group43Var5,
    Group43Var6,
    Group43Var7,
    Group43Var8)

  def id: Byte = 43
}

import io.stepfunc.dnp3c.dnp3.app.Fields._

object Group43Var0 extends EmptyVariation(Group43, 0) with TypeTraits.EventVariationZero with TypeTraits.Level4

object Group43Var1 extends FixedSize(Group43, 1, List(status, value32)) with TypeTraits.Event with TypeTraits.Level4

object Group43Var2 extends FixedSize(Group43, 2, List(status, value16)) with TypeTraits.Event with TypeTraits.Level4

object Group43Var3 extends FixedSize(Group43, 3, List(status, value32, time48)) with TypeTraits.Event
    with TypeTraits.Level4

object Group43Var4 extends FixedSize(Group43, 4, List(status, value16, time48)) with TypeTraits.Event
    with TypeTraits.Level4

object Group43Var5 extends FixedSize(Group43, 5, List(status, valueF32)) with TypeTraits.Event with TypeTraits.Level4

object Group43Var6 extends FixedSize(Group43, 6, List(status, valueF64)) with TypeTraits.Event with TypeTraits.None

object Group43Var7 extends FixedSize(Group43, 7, List(status, valueF32, time48)) with TypeTraits.Event
    with TypeTraits.Level4

object Group43Var8 extends FixedSize(Group43, 8, List(status, valueF64, time48)) with TypeTraits.Event
    with TypeTraits.None
