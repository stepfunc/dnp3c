// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.s86.s864

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ ControlOptions, SupportOptions }
import io.stepfunc.dnp3c.conformance.s8.s86.s864.SupervisoryControl._
import io.stepfunc.dnp3c.dnp3.app.Sequence
import io.stepfunc.dnp3c.dnp3.app.objects.CommandStatus

// global remote supervisory control, as TB2017-001 replaces the test: every binary output goes to local mode, which
// clears ONLINE and refuses controls, and back
object Procedure8642 extends ProcedureDefinition {

  override protected def requiredOptions: List[OptionCondition] = List(
    SupportOptions.globalRemoteSupervisoryControl,
    SupportOptions.Outputs.binary.statusPoints,
    SupportOptions.Outputs.binary.commands)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    val index = options.get(ControlOptions.primary.index)
    val first = new Snapshot
    List(
      step('a') --> PluginSteps.enableRemoteSupervisoryControl(),
      step('b') --> describe(
        s"Binary output ${index.value}, the one the controls go to, must be online and have no exception flags"),
      step('c') --> read(Sequence.zero),
      stepRange('d', 'e') -->
        response(Sequence.zero, localControl = false, record(first) && onlineWithoutExceptions(List(index.value))),
      step('f') --> PluginSteps.setGlobalRemoteSupervisoryControl(),
      step('g') --> read(Sequence.one),
      stepRange('h', 'i') -->
        response(Sequence.one, localControl = true, localAndRemote(first, _ => true, Nil, "each binary output")),
      step('j') --> operate(options, index, Sequence.two),
      step('k') --> echo(options, index, Sequence.two, CommandStatus.LOCAL, localControl = true),
      step('l') --> doesNotOperate,
      step('m') --> PluginSteps.enableRemoteSupervisoryControl(),
      step('n') --> read(Sequence.three),
      step('o') --> response(Sequence.three, localControl = false, asBefore(first, Set.empty)),
      step('p') --> operate(options, index, Sequence.four),
      step('q') --> echo(options, index, Sequence.four, CommandStatus.SUCCESS, localControl = false),
      step('r') --> operates(index)
    )
  }
}
