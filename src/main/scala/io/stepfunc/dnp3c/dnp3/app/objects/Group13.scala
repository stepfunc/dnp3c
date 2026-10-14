// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._

// binary output command events: the command's status in bits 0 to 6, and the commanded state in bit 7
object Group13 extends Group {
  def variations: List[Variation] = List(Group13Var0, Group13Var1, Group13Var2)

  def id: Byte = 13
}

import io.stepfunc.dnp3c.dnp3.app.Fields._

object Group13Var0 extends EmptyVariation(Group13, 0) with TypeTraits.EventVariationZero with TypeTraits.Level4

object Group13Var1 extends FixedSize(Group13, 1, List(status)) with TypeTraits.Event with TypeTraits.Level4

object Group13Var2 extends FixedSize(Group13, 2, List(status, time48)) with TypeTraits.Event with TypeTraits.Level4
