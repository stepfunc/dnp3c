// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._

object Group11 extends Group {
  def variations: List[Variation] = List(Group11Var0, Group11Var1, Group11Var2)

  def id: Byte = 11
}

object Group11Var0 extends EmptyVariation(Group11, 0) with TypeTraits.EventVariationZero with TypeTraits.Level4
object Group11Var1 extends FixedSize(Group11, 1, List(Fields.flagsWithBinary), BinaryOutputStatusBuilder)
    with TypeTraits.Event with TypeTraits.Level4
object Group11Var2
    extends FixedSize(Group11, 2, List(Fields.flagsWithBinary, Fields.time48), BinaryOutputStatusBuilder)
    with TypeTraits.Event with TypeTraits.Level4
