// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._

object Group33 extends Group {
  def variations: List[Variation] =
    List(Group33Var1, Group33Var2, Group33Var3, Group33Var4, Group33Var5, Group33Var6, Group33Var7, Group33Var8)

  def id: Byte = 33
}

import io.stepfunc.dnp3c.dnp3.app.Fields._

object Group33Var1 extends FixedSize(Group33, 1, List(flags, value32), EventFrozenAnalogBuilder) with TypeTraits.Event
    with TypeTraits.None

object Group33Var2 extends FixedSize(Group33, 2, List(flags, value16), EventFrozenAnalogBuilder) with TypeTraits.Event
    with TypeTraits.None

object Group33Var3 extends FixedSize(Group33, 3, List(flags, value32, time48), EventFrozenAnalogBuilder)
    with TypeTraits.Event with TypeTraits.None

object Group33Var4 extends FixedSize(Group33, 4, List(flags, value16, time48), EventFrozenAnalogBuilder)
    with TypeTraits.Event with TypeTraits.None

object Group33Var5 extends FixedSize(Group33, 5, List(flags, valueF32), EventFrozenAnalogBuilder) with TypeTraits.Event
    with TypeTraits.None

object Group33Var6 extends FixedSize(Group33, 6, List(flags, valueF64), EventFrozenAnalogBuilder) with TypeTraits.Event
    with TypeTraits.None

object Group33Var7 extends FixedSize(Group33, 7, List(flags, valueF32, time48), EventFrozenAnalogBuilder)
    with TypeTraits.Event with TypeTraits.None

object Group33Var8 extends FixedSize(Group33, 8, List(flags, valueF64, time48), EventFrozenAnalogBuilder)
    with TypeTraits.Event with TypeTraits.None
