// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s6.s66.s662

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s6._
import io.stepfunc.dnp3c.conformance.s6.s66.s662
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps.send
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.link.{ Control, LinkFunction }
import io.stepfunc.dnp3c.dnp3.{ LinkOverride, Requests }
import io.stepfunc.dnp3c.protocol.Hex

object Procedure6625 extends ProcedureDefinition {

  override def steps(options: OptionSource): Iterable[LabeledStep] = {

    val integrity = Requests.readClass0(Sequence.zero)
    val expectedTimeoutOrNotSupported =
      expectLinkTimeoutOr(withFunction(LinkFunction.NOT_SUPPORTED), s662.Section.severalSecondsMs)

    // e.g. "... with link control 0xC3 (CONFIRMED_USER_DATA, FCV wrongly cleared)"
    def text(control: Byte): String = {
      val parsed = Control(control)
      val fcv = if (parsed.fcv) "set" else "cleared"
      s"Send a READ for class 0 (g60v1, qualifier 0x06) with link control " +
        s"${Hex.prefixed(control)} (${parsed.func.description}, FCV wrongly $fcv)"
    }

    def steps1to4: List[LabeledStep] = {
      List(
        (1 to 2) --> (describe(text(0xC3.toByte)) + send(integrity, LinkOverride.control(0xC3.toByte))),
        (3 to 4) --> expectedTimeoutOrNotSupported
      )
    }

    def steps5to8(control: Byte): Iterable[LabeledStep] = {
      List(
        (5 to 6) --> (describe(text(control)) + send(integrity, LinkOverride.control(control))),
        (7 to 8) --> expectedTimeoutOrNotSupported
      )
    }

    // steps 5-8 use 0xD4, then step 9 repeats them with the other blocks
    val others: List[Byte] = List(0xC2.toByte, 0xD0.toByte, 0xD9.toByte)
    val step9 = 9 --> describe(
      "Run steps 5-8 again with each of the control blocks " + others.map(b => Hex.prefixed(b)).mkString(", "))

    s662.Section.prologue ++ steps1to4 ++ steps5to8(0xD4.toByte) ++ List(step9) ++ others.flatMap(b => steps5to8(b))
  }

}
