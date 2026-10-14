// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s6.s66.s663

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s6._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.dnp3.LinkOverride
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.link.LinkFunction

object Section extends TestSection {

  override def id = "6.6.3"
  override def description = "Secondary Frames"

  override def procedures: List[TestProcedure] = List(
    TestProcedure("6.6.3.1", "Invalid Start Octets", Procedure6631),
    TestProcedure("6.6.3.2", "Invalid Secondary Function Code", Procedure6632),
    TestProcedure("6.6.3.3", "Invalid Destination Address", Procedure6633),
    TestProcedure("6.6.3.4", "Invalid CRC", Procedure6634)
  )

  def prologue(options: OptionSource, confirms: Undoable): Iterable[LabeledStep] = {

    val postfix = " - (6.6.3 prologue)"

    List(
      (1 to 2) --> confirms.apply + PluginSteps.cyclePower,
      3 --> readClass0(Sequence.zero, LinkOverride.control(0xC4.toByte)),
      4 --> (expectLinkReset(responseTimeoutMs(options)) + sendLinkConfirm),
      (5 to 6) --> expectAtLeastOneResponse(Sequence.zero, withFunction(LinkFunction.CONFIRMED_USER_DATA))
    ).map(x => x.appendLabel(postfix))
  }

  // a procedure's checks, then its repeat step and the checks again with other values, each after the prologue; the
  // cleanup turns link layer confirms back off, so later procedures run without them
  def twice(
      options: OptionSource,
      first: Iterable[LabeledStep],
      repeat: LabeledStep,
      second: Iterable[LabeledStep]): Plan = {
    val confirms =
      Undoable(PluginSteps.setLinkLayerConfirm(true), PluginSteps.setLinkLayerConfirm(false) + PluginSteps.cyclePower)
    Plan(
      prologue(options, confirms) ++ first ++ List(repeat) ++ prologue(options, confirms) ++ second,
      List(Label("6.6.3 epilogue") --> confirms.restore))
  }

}
