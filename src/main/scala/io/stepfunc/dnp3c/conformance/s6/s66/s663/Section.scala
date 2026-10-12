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
    Procedure6631,
    Procedure6632,
    Procedure6633,
    Procedure6634
  )

  def prologue(options: OptionSource): Iterable[LabeledStep] = {

    val postfix = " - (6.6.3 prologue)"

    List(
      (1 to 2) --> PluginSteps.setLinkLayerConfirm(true) + PluginSteps.cyclePower,
      3 --> readClass0(Sequence.zero, LinkOverride.control(0xC4.toByte)),
      4 --> (expectLinkReset(responseTimeoutMs(options)) + sendLinkConfirm),
      (5 to 6) --> expectAtLeastOneResponse(Sequence.zero, withFunction(LinkFunction.CONFIRMED_USER_DATA))
    ).map(x => x.appendLabel(postfix))
  }

  // a procedure's checks, then its repeat step and the checks again with other values, each after the prologue
  def twice(
      options: OptionSource,
      first: Iterable[LabeledStep],
      repeat: LabeledStep,
      second: Iterable[LabeledStep]): Iterable[LabeledStep] =
    prologue(options) ++ first ++ List(repeat) ++ prologue(options) ++ second ++ epilogue

  // turn link layer confirms back off, so later procedures run without them
  def epilogue: Iterable[LabeledStep] =
    List(Label("6.6.3 epilogue") --> PluginSteps.setLinkLayerConfirm(false) + PluginSteps.cyclePower)

}
