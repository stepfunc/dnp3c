// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s6.s67

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions
import io.stepfunc.dnp3c.conformance.s6._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.{ LinkOverride, Requests }
import io.stepfunc.dnp3c.protocol.parsing.UInt16LE

object Procedure672 extends ProcedureDefinition {

  override protected def requiredOptions = List(SupportOptions.selfAddress)

  val selfAddress: UInt16LE = UInt16LE(0xFFFC)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {

    List(
      1 --> describe("Device supports self-address"),
      2 --> PluginSteps.setLinkLayerSelfAddress(true),
      3 --> PluginSteps.cyclePower,
      4 --> send(Requests.readClass0(Sequence.zero), LinkOverride.destination(selfAddress)),
      5 --> expectAtLeastOneResponse(Sequence.zero),
      6 --> PluginSteps.setLinkLayerSelfAddress(false),
      7 --> PluginSteps.cyclePower + send(Requests.readClass0(Sequence.zero), LinkOverride.destination(selfAddress)),
      8 --> expectNoAnswer(options)
    )
  }

}
