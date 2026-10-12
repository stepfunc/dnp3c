// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.dnp3.app

case class BooleanFlag(value: Boolean)

sealed class First(value: Boolean) extends BooleanFlag(value)
object First {
  object Yes extends First(true)
  object No extends First(false)

  def apply(value: Boolean): First = {
    if (value) First.Yes else First.No
  }
}

sealed class Final(value: Boolean) extends BooleanFlag(value)
object Final {
  object Yes extends Final(true)
  object No extends Final(false)

  def apply(value: Boolean): Final = {
    if (value) Final.Yes else Final.No
  }
}

sealed class Confirm(value: Boolean) extends BooleanFlag(value)
object Confirm {
  object Yes extends Confirm(true)
  object No extends Confirm(false)

  def apply(value: Boolean): Confirm = {
    if (value) Confirm.Yes else Confirm.No
  }
}

sealed class Unsolicited(value: Boolean) extends BooleanFlag(value)
object Unsolicited {
  object Yes extends Unsolicited(true)
  object No extends Unsolicited(false)

  def apply(value: Boolean): Unsolicited = {
    if (value) Unsolicited.Yes else Unsolicited.No
  }
}
