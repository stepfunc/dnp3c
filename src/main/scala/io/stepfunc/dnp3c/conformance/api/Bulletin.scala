// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.api

/**
 * A DNP Users Group technical bulletin that a device can declare it implements (outstation.bulletins), where it changes
 * what a revision's steps check, or makes a requirement they leave implicit. A run checks a revision as published
 * unless the configuration lists the bulletin, and each check it changes names it.
 */
enum Bulletin(val id: String) {

  // TB2017-003a 5.1.3.1.2: binary and double-bit input events recorded while the clock is unsynchronized are reported
  // with relative time after an unsynchronized CTO (g51v2), never with absolute time
  case TB2017_003a extends Bulletin("TB2017-003a")
}

object Bulletin {

  def fromId(id: String): Option[Bulletin] = values.find(_.id.equalsIgnoreCase(id.trim))
}
