// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.application

import io.stepfunc.dnp3c.protocol.logging.Colors
import org.fusesource.jansi.Ansi.Color._

object Logo {

  private val logo = List(
    """       __           _____             """,
    """  ____/ /___  ____ |__  /_____         """,
    """ / __  / __ \/ __ \ /_ </ ___/         """,
    """/ /_/ / / / / /_/ /__/ / /__           """,
    """\__,_/_/ /_/ .___/____/\___/           """,
    """          /_/                          """
  )

  private val copyright = List(
    """                                       """,
    """DNP3 conformance testing engine        """,
    """                                       """,
    """Copyright Step Function I/O LLC        """,
    """https://stepfunc.io                    """,
    """                                       """
  )

  private val color1 = Colors(GREEN, BLACK)
  private val color2 = Colors(WHITE, BLACK)

  def print(): Unit = {
    logo.foreach(color1.printLine)
    copyright.foreach(color2.printLine)
  }
}
