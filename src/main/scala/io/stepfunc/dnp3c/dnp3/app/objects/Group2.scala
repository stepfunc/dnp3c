// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._

object Group2 extends Group {
  def variations: List[Variation] = List(Group2Var0, Group2Var1, Group2Var2, Group2Var3)

  def id: Byte = 2
}

import io.stepfunc.dnp3c.dnp3.app.Fields._

object Group2Var0 extends EmptyVariation(Group2, 0) with TypeTraits.EventVariationZero with TypeTraits.Level2

object Group2Var1 extends FixedSize(Group2, 1, List(flagsWithBinary), EventBinaryBuilder) with TypeTraits.Event
    with TypeTraits.Level1

object Group2Var2 extends FixedSize(Group2, 2, List(flagsWithBinary, time48), EventBinaryBuilder) with TypeTraits.Event
    with TypeTraits.Level1

object Group2Var3 extends FixedSize(Group2, 3, List(flagsWithBinary, timeCTO), EventBinaryBuilder)
    with TypeTraits.CTOEvent with TypeTraits.Level1
