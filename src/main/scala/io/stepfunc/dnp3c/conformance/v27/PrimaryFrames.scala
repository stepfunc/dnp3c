// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.common.LinkSteps._
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.dnp3.{ LinkOverride, Requests, TestDriver }
import io.stepfunc.dnp3c.dnp3.link.{ Control, LinkFrame }
import io.stepfunc.dnp3c.dnp3.transport.Transport
import io.stepfunc.dnp3c.dnp3.app.Sequence

// what 6.6.2's procedures share
object PrimaryFrames {

  // "Wait several seconds" before verifying that the DUT did not respond
  val severalSecondsMs = 3000

  // a class 0 read (g60v1, qualifier 0x06) in one transport segment, in a confirmed user data frame (control 0xD3)
  // unless another control is given
  def class0Frame(driver: TestDriver, control: Control = Control(0xD3.toByte)): LinkFrame = {
    val segment = Transport.function(fir = true, fin = true, seq = 0) :: Requests.readClass0(Sequence.zero).toBytes
    LinkFrame.withData(control, driver.remote, driver.local, segment)
  }

  // These common steps are run before every other test in 6.6.2
  def prologue: Iterable[LabeledStep] = List(
    1 --> PluginSteps.cyclePower,
    2 --> sendLinkControl(0xC0.toByte),
    3 --> expectFrame(isConfirm),
    4 --> readClass0(Sequence.zero, LinkOverride.control(0xF3.toByte)),
    5 --> expectFrame(isConfirm),
    6 --> expectAtLeastOneResponse(Sequence.zero)
  ).map(x => x.appendLabel(" - (6.6.2 prologue)"))
}
