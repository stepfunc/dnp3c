// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.protocol.parsing.{ Field, UInt48LE }

object Group50 extends Group {
  def variations: List[Variation] = List(Group50Var1, Group50Var2, Group50Var3, Group50Var4)

  def id: Byte = 50
}

import io.stepfunc.dnp3c.dnp3.app.Fields._

object Group50Var1 extends FixedSize(Group50, 1, List(time48)) with TypeTraits.Information with TypeTraits.Level1 {
  def apply(time: UInt48LE): Field.Collection = {
    Field.collect(Fields.time48(time))
  }
}
object Group50Var2 extends FixedSize(Group50, 2, List(time48, interval32)) with TypeTraits.Information
    with TypeTraits.None
object Group50Var3 extends FixedSize(Group50, 3, List(time48)) with TypeTraits.Information with TypeTraits.Level4
object Group50Var4 extends FixedSize(Group50, 4, List(time48, count32, value8)) with TypeTraits.Information
    with TypeTraits.None
