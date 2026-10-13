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
class GlobalRemoteSupervisoryControl(n: Numbering) extends ProcedureDefinition {

  override protected def requiredOptions: List[OptionCondition] = List(
    SupportOptions.globalRemoteSupervisoryControl,
    SupportOptions.Outputs.binary.statusPoints,
    SupportOptions.Outputs.binary.commands)

  override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
    val index = options.get(ControlOptions.primary.index)
    val first = new Snapshot
    List(
      n.step(1) --> PluginSteps.enableRemoteSupervisoryControl(),
      n.step(2) --> describe(
        s"Binary output ${index.value}, the one the controls go to, must be online and have no exception flags"),
      n.step(3) --> read(Sequence.zero),
      n.steps(4, 5) -->
        response(Sequence.zero, localControl = false, record(first) && onlineWithoutExceptions(List(index.value))),
      n.step(6) --> PluginSteps.setGlobalRemoteSupervisoryControl(),
      n.step(7) --> read(Sequence.one),
      n.steps(8, 9) -->
        response(Sequence.one, localControl = true, localAndRemote(first, _ => true, Nil, "each binary output")),
      n.step(10) --> operate(options, index, Sequence.two),
      n.step(11) --> echo(options, index, Sequence.two, CommandStatus.LOCAL, localControl = true),
      n.step(12) --> doesNotOperate,
      n.step(13) --> PluginSteps.enableRemoteSupervisoryControl(),
      n.step(14) --> read(Sequence.three),
      n.step(15) --> response(Sequence.three, localControl = false, asBefore(first, Set.empty)),
      n.step(16) --> operate(options, index, Sequence.four),
      n.step(17) --> echo(options, index, Sequence.four, CommandStatus.SUCCESS, localControl = false),
      n.step(18) --> operates(index)
    )
  }
}

// as 2.7 runs it, TB2017-001's test, with its steps numbered 1.a, 1.b, ...
object Procedure8642 extends GlobalRemoteSupervisoryControl(Numbering.lettered)
