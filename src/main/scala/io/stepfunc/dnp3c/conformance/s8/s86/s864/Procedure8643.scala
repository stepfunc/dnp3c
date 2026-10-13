// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s86.s864

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ FlagTestOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.s8.s86.s864.SupervisoryControl._
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.app.objects.CommandStatus

// individual remote supervisory control, as TB2017-001 replaces the test: one binary output goes to local mode, which
// clears its ONLINE and refuses controls, while another stays in remote mode and operates; then both are remote again
object Procedure8643 extends ProcedureDefinition {

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
      step('a') --> PluginSteps.enableRemoteSupervisoryControl(),
      step('b') --> describe(
        s"Binary outputs ${local.value} and ${remote.value} must be online, have no exception flags, and be able to " +
          "go to local mode separately"),
      step('c') --> read(Sequence.zero),
      stepRange('d', 'e') --> response(
        Sequence.zero,
        localControl = false,
        record(first) && onlineWithoutExceptions(List(local.value, remote.value))),
      step('f') --> PluginSteps.setIndividualRemoteSupervisoryControl(local.value),
      step('g') --> read(Sequence.one),
      stepRange('h', 'i') --> response(
        Sequence.one,
        localControl = true,
        localAndRemote(first, _ == local.value, List(remote.value), s"binary output ${local.value}")),
      step('j') --> operate(options, local, Sequence.two),
      step('k') --> echo(options, local, Sequence.two, CommandStatus.LOCAL, localControl = true),
      step('l') --> doesNotOperate,
      step('m') --> operate(options, remote, Sequence.three),
      // an output is still in local mode, so IIN1.5 stays set
      step('n') --> echo(options, remote, Sequence.three, CommandStatus.SUCCESS, localControl = true),
      step('o') --> operates(remote),
      step('p') --> PluginSteps.enableRemoteSupervisoryControl(),
      step('q') --> read(Sequence.four),
      // the output that operated may have changed state
      stepRange('r', 's') --> response(Sequence.four, localControl = false, asBefore(first, Set(remote.value))),
      step('t') --> operate(options, local, Sequence.five),
      step('u') --> echo(options, local, Sequence.five, CommandStatus.SUCCESS, localControl = false),
      step('v') --> operates(local)
    )
  }
}
