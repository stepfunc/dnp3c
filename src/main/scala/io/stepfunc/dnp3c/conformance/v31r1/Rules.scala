// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.dnp3.MessagePolicy
import io.stepfunc.dnp3c.dnp3.app.{ AppFunction, Event, IIN, Message }

/** 3.1r1's rules for every message a procedure receives, beyond what each test checks */
object Rules {

  /**
   * Section 2, Note 7: a response asks for confirmation only where the test, or the protocol, requires it. The protocol
   * requires it of a response carrying events, of a fragment that another follows, of every unsolicited response, and of
   * a response that reports a broadcast (IIN1.0) the outstation wants acknowledged (IEEE 1815-2012 4.5.1); so this rule
   * fails a final solicited fragment without events or IIN1.0 that asks for confirmation. A test that leaves the choice
   * to the outstation exempts its read.
   */
  val confirmOnlyWhenRequired: MessagePolicy = new MessagePolicy {

    def id: String = "Note 7"

    def description: String =
      "a response asks for confirmation only when it carries events, another fragment follows, or it reports a broadcast"

    def violation(message: Message): Option[String] = {
      val events = message.headers.exists(_.variation.objectType == Event)
      val broadcast = message.iin.exists(iin => (iin & IIN.BROADCAST) == IIN.BROADCAST)
      val last = message.ctrl.fin.value
      Option.when(message.function == AppFunction.response && message.ctrl.con.value && last && !events &&
        !broadcast)(
        "it asks for confirmation, but it's the final fragment, with no events and no IIN1.0")
    }

  }

  val all: List[MessagePolicy] = List(confirmOnlyWhenRequired)
}
