// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.common.LinkSteps._

object Procedure6_3_2 extends ProcedureDefinition {

  override def steps(options: OptionSource): List[LabeledStep] = {
    List(
      1 --> PluginSteps.cyclePower,
      2 --> sendLinkControl(0xC9.toByte),
      3 --> expectFrame(withAnyControl(Set(0x0B, 0x1B))),
      4 --> sendLinkControl(0xE9.toByte),
      5 --> expectFrame(withAnyControl(Set(0x0B, 0x1B)))
    )
  }

}
