// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._

object Group10 extends Group {
  def variations: List[Variation] = List(Group10Var0, Group10Var1, Group10Var2)

  def id: Byte = 10
}

object Group10Var0 extends EmptyVariation(Group10, 0) with TypeTraits.StaticVariationZero with TypeTraits.Level1

object Group10Var1 extends SingleBitField(Group10, 1, true) with TypeTraits.Static with TypeTraits.None

object Group10Var2 extends FixedSize(Group10, 2, List(Fields.flagsWithBinary), StaticBinaryOutputStatusBuilder)
    with TypeTraits.Static with TypeTraits.Level1
