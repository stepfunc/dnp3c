// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._

object Group3 extends Group {
  def variations: List[Variation] = List(Group3Var0, Group3Var1, Group3Var2)

  def id: Byte = 3
}

import io.stepfunc.dnp3c.dnp3.app.Fields._

object Group3Var0 extends EmptyVariation(Group3, 0) with TypeTraits.StaticVariationZero with TypeTraits.Level4

object Group3Var1 extends DoubleBitField(Group3, 1) with TypeTraits.Static with TypeTraits.Level4

object Group3Var2 extends FixedSize(Group3, 2, List(flagsWithDoubleBit), StaticDoubleBitBuilder) with TypeTraits.Static
    with TypeTraits.Level4
