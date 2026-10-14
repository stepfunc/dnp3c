// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.dnp3.MessagePolicy
import io.stepfunc.dnp3c.dnp3.app.{ AppFunction, CommonTimeOfOccurrence, Event, IIN, Message }

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

  /**
   * 8.11.1, Runtime Behavior: an unsolicited response carries only event data, with the CTOs that time it. 2.7 also
   * allowed the static g10v2 and g40v2, for which there are no events.
   */
  val unsolicitedOnlyEvents: MessagePolicy = new MessagePolicy {

    def id: String = "8.11.1"

    def description: String = "an unsolicited response carries only event data"

    def violation(message: Message): Option[String] = {
      val others = message.headers.map(_.variation).filterNot(v =>
        v.objectType == Event || v.objectType == CommonTimeOfOccurrence)
      Option.when(message.function == AppFunction.unsolicited && others.nonEmpty)(
        s"it's unsolicited, but carries ${others.distinct.mkString(", ")}, which aren't events")
    }
  }

  val all: List[MessagePolicy] = List(confirmOnlyWhenRequired, unsolicitedOnlyEvents)
}
