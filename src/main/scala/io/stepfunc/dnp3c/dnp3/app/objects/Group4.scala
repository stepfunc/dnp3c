// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._

object Group4 extends Group {
  def variations: List[Variation] = List(Group4Var0, Group4Var1, Group4Var2, Group4Var3)

  def id: Byte = 4
}

import io.stepfunc.dnp3c.dnp3.app.Fields._

object Group4Var0 extends EmptyVariation(Group4, 0) with TypeTraits.EventVariationZero with TypeTraits.Level4

object Group4Var1 extends FixedSize(Group4, 1, List(flagsWithDoubleBit), DoubleBitBuilder) with TypeTraits.Event
    with TypeTraits.Level4

object Group4Var2 extends FixedSize(Group4, 2, List(flagsWithDoubleBit, time48), DoubleBitBuilder)
    with TypeTraits.Event with TypeTraits.Level4

object Group4Var3 extends FixedSize(Group4, 3, List(flagsWithDoubleBit, timeCTO), DoubleBitBuilder)
    with TypeTraits.Event with TypeTraits.Level4
