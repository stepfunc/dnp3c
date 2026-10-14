// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.{ InputType, Validations }
import io.stepfunc.dnp3c.conformance.s8.s815.RelativeTimeSupported
import io.stepfunc.dnp3c.dnp3.{ AutoConfirm, Requests }
import io.stepfunc.dnp3c.dnp3.app.{ IIN, Sequence }
import io.stepfunc.dnp3c.dnp3.app.objects.Group2Var3

/**
 * 3.1r1's 8.15. 8.15.2 reads one binary input event with relative time, where 2.7 skipped the section for a device
 * without them: the response must follow 8.14.1.1. 8.15.3 is 2.7's 8.15.2, without its first step, which stated the
 * condition. It runs where 8.15.2's response, by those rules, has events with relative time: for a device that records
 * events with time, relative or absolute, and doesn't reject the read.
 */
object CommonTime {

  private val input = InputType.BinaryInput

  private val eventReads = OptionCondition(input.support.eventReads)

  /** 8.15.2 */
  val readWithRelativeTime: ProcedureDefinition = new ProcedureDefinition {
    override protected def requiredOptions: List[OptionCondition] = input.support.withEvents :+ eventReads

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = List(
      1 --> PluginSteps.generateExactBinaryInputChangeEvents(1),
      2 --> send(Requests.readAllObjects(Sequence.one, Group2Var3)),
      3 -->
        (if (options.get(input.withRelativeTimeRejected))
           expectSingleResponse(Sequence.one, Validations.isEmpty && Validations.containing(IIN.OBJECT_UNKNOWN)) +
             noting(InputEvents.relativeCompliantWith2012Only)
         else
           // confirmed, though the step doesn't say to, so the event isn't left for the tests that follow
           expectSingleResponse(
             Sequence.one,
             AutoConfirm.Yes,
             Validations.eventResponse(
               PluginValidations.verifyLatestBinaryInputChangeEvents(),
               EventRules.consistent(input, Group2Var3, options))
           )),
      4 --> describe(
        "If the response has no binary input events with relative time, no further 8.15 tests apply: 8.15.3 runs " +
          "only where the DUT records events with time and doesn't reject the read")
    )
  }

  // 8.14.1.1 has a read of g2v3 answered with g2v3 by a device whose events have time, unless it rejects the read
  private val reportsRelativeTime: Requirement = Requirement.all(
    !input.withRelativeTimeRejected,
    Requirement.anyOf(OptionCondition(input.withRelativeTime), OptionCondition(input.withTime)))

  /** 8.15.3 */
  val relativeTimeSupported: ProcedureDefinition =
    new StaticInputs.Requiring(new RelativeTimeSupported(conditionStep = false, reportsRelativeTime), eventReads)
}
