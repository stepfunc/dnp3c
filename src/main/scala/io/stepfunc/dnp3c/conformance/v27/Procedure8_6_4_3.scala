// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v27

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ FlagTestOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.common.SupervisoryControl._
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.app.objects.CommandStatus

// individual remote supervisory control, as TB2017-001 replaces the test: one binary output goes to local mode, which
// clears its ONLINE and refuses controls, while another stays in remote mode and operates; then both are remote again
class IndividualRemoteSupervisoryControl(n: Numbering) extends ProcedureDefinition {

  override protected def requiredOptions: List[OptionCondition] = List(
    SupportOptions.individualRemoteSupervisoryControl,
    SupportOptions.Outputs.binary.statusPoints,
    SupportOptions.Outputs.binary.commands)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    val local = options.get(FlagTestOptions.individualRemoteSupervisoryOutputIndex)
    val remote = options.get(FlagTestOptions.individualRemoteSupervisoryRemoteOutputIndex)
    if (local == remote)
      throw new Exception(
        s"${FlagTestOptions.individualRemoteSupervisoryOutputIndex.id} and " +
          s"${FlagTestOptions.individualRemoteSupervisoryRemoteOutputIndex.id} must be different binary outputs")

    val first = new Snapshot
    List(
      n.step(1) --> PluginSteps.enableRemoteSupervisoryControl(),
      n.step(2) --> describe(
        s"Binary outputs ${local.value} and ${remote.value} must be online, have no exception flags, and be able to " +
          "go to local mode separately"),
      n.step(3) --> read(Sequence.zero),
      n.steps(4, 5) --> response(
        Sequence.zero,
        localControl = false,
        record(first) && onlineWithoutExceptions(List(local.value, remote.value))),
      n.step(6) --> PluginSteps.setIndividualRemoteSupervisoryControl(local.value),
      n.step(7) --> read(Sequence.one),
      n.steps(8, 9) --> response(
        Sequence.one,
        localControl = true,
        localAndRemote(first, _ == local.value, List(remote.value), s"binary output ${local.value}")),
      n.step(10) --> operate(options, local, Sequence.two),
      n.step(11) --> echo(options, local, Sequence.two, CommandStatus.LOCAL, localControl = true),
      n.step(12) --> doesNotOperate,
      n.step(13) --> operate(options, remote, Sequence.three),
      // an output is still in local mode, so IIN1.5 stays set
      n.step(14) --> echo(options, remote, Sequence.three, CommandStatus.SUCCESS, localControl = true),
      n.step(15) --> operates(remote),
      n.step(16) --> PluginSteps.enableRemoteSupervisoryControl(),
      n.step(17) --> read(Sequence.four),
      // the output that operated may have changed state
      n.steps(18, 19) --> response(Sequence.four, localControl = false, asBefore(first, Set(remote.value))),
      n.step(20) --> operate(options, local, Sequence.five),
      n.step(21) --> echo(options, local, Sequence.five, CommandStatus.SUCCESS, localControl = false),
      n.step(22) --> operates(local)
    )
  }
}

// as 2.7 runs it, TB2017-001's test, with its steps numbered 1.a, 1.b, ...
object Procedure8_6_4_3 extends IndividualRemoteSupervisoryControl(Numbering.lettered)
