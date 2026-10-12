// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._

object Group80 extends Group {
  def variations: List[Variation] = List(Group80Var1)

  def id: Byte = 80
}

object Group80Var1 extends SingleBitField(Group80, 1, false) with TypeTraits.Information with TypeTraits.Level1
