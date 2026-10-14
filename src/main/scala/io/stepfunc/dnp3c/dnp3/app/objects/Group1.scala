// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._

object Group1 extends Group {
  def variations: List[Variation] = List(Group1Var0, Group1Var1, Group1Var2)

  def id: Byte = 1
}

object Group1Var0 extends EmptyVariation(Group1, 0) with TypeTraits.StaticVariationZero with TypeTraits.Level1

object Group1Var1 extends SingleBitField(Group1, 1, false) with TypeTraits.Static with TypeTraits.Level1

object Group1Var2 extends FixedSize(Group1, 2, List(Fields.flagsWithBinary), BinaryBuilder) with TypeTraits.Static
    with TypeTraits.Level2
