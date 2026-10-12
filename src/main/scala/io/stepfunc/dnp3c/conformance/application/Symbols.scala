// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application

import io.stepfunc.dnp3c.protocol.logging.Level

object Symbols {

  // messages logged by the integration plugin
  val plugin = "[PLG]"

  def rx(level: Level): String = level match {
    case Level.One => "<-P--"
    case Level.Two => "<-L--"
    case Level.Three => "<-T--"
    case Level.Four => "<-A--"
  }

  def tx(level: Level): String = level match {
    case Level.One => "--P->"
    case Level.Two => "--L->"
    case Level.Three => "--T->"
    case Level.Four => "--A->"
  }

}
