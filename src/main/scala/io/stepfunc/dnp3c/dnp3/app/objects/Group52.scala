// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._

object Group52 extends Group {
  def variations: List[Variation] = List(Group52Var1, Group52Var2)

  def id: Byte = 52
}

import io.stepfunc.dnp3c.dnp3.app.Fields._

object Group52Var1 extends FixedSize(Group52, 1, List(delay16), TimeOffsetBuilder) with TypeTraits.Information
    with TypeTraits.Level1
object Group52Var2 extends FixedSize(Group52, 2, List(delay16), TimeOffsetBuilder) with TypeTraits.Information
    with TypeTraits.Level1
