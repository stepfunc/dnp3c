// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._

object Group51 extends Group {
  def variations: List[Variation] = List(Group51Var1, Group51Var2)

  def id: Byte = 51
}

import io.stepfunc.dnp3c.dnp3.app.Fields._

object Group51Var1 extends FixedSize(Group51, 1, List(time48), TimeOffsetBuilder) with TypeTraits.CommonTimeOfOccurrence
    with TypeTraits.Level1
object Group51Var2 extends FixedSize(Group51, 2, List(time48), TimeOffsetBuilder) with TypeTraits.CommonTimeOfOccurrence
    with TypeTraits.Level1
