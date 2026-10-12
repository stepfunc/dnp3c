// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._

object Group60 extends Group {
  def variations: List[Variation] = List(Group60Var1, Group60Var2, Group60Var3, Group60Var4)

  def id: Byte = 60
}

sealed trait ClassGroupVariation extends TypeTraits.Information {
  def clazz: Int
}

sealed trait EventClassGroupVariation extends ClassGroupVariation

object Group60Var1 extends EmptyVariation(Group60, 1) with TypeTraits.Level1 with ClassGroupVariation {
  def clazz = 0
}

object Group60Var2 extends EmptyVariation(Group60, 2) with TypeTraits.Level1 with EventClassGroupVariation {
  def clazz = 1
}

object Group60Var3 extends EmptyVariation(Group60, 3) with TypeTraits.Level1 with EventClassGroupVariation {
  def clazz = 2
}

object Group60Var4 extends EmptyVariation(Group60, 4) with TypeTraits.Level1 with EventClassGroupVariation {
  def clazz = 3
}
