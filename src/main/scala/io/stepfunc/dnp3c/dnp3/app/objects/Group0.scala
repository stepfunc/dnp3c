// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app.objects

import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.protocol.parsing.Field

// device attributes: each variation is one attribute, and the object index is the attribute set (0 is the standard set)
object Group0 extends Group {

  def id: Byte = 0

  val variations: List[Variation] = (1 to 255).map(v => Group0Var(v.toByte)).toList

  def apply(variation: Int): Variation = variations(variation - 1)

  // a visible string attribute value, e.g. for a WRITE
  def visibleString(value: String): Field.Collection = Field.collect(Fields.attribute(AttributeValue.encode(value)))
}

final class Group0Var(id: Byte) extends FixedSize(Group0, id, List(Fields.attribute)) with TypeTraits.Attribute
    with TypeTraits.None
