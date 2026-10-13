// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.s8.helpers

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.OutstationOptions
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.dnp3.Requests
import io.stepfunc.dnp3c.dnp3.app.Sequence

/**
 * The procedures that 8.14 (binary input events) and 8.22 (double-bit input events) both have, each written once for
 * either input. A procedure object extends one of these, and the section that lists it gives it a number and title.
 */
object InputEventProcedures {

  sealed abstract class InputEventProcedure(val input: InputType) extends ProcedureDefinition {
    protected def events: InputEventSteps = new InputEventSteps(input)
  }

  // an input event procedure for a DUT with the input's events, and the time option the procedure needs, if any
  sealed abstract class EventProcedure(input: InputType, time: List[BooleanOption] = Nil)
      extends InputEventProcedure(input) {
    override protected def requiredOptions: List[OptionCondition] =
      input.support.withEvents ++ time.map(OptionCondition(_))
  }

  // a DUT without the input's events must answer a read for them with a Null Response
  abstract class NoEvents(input: InputType) extends InputEventProcedure(input) {
    override def disabledBy(options: OptionSource): Option[OptionCondition] =
      input.support.disabledUnlessWithoutEvents(options)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = List(
      1 --> send(Requests.readAllObjects(Sequence.one, input.anyVariation)),
      2 --> reportByExceptionNotVerified(Some(s"${input.name}s")),
      3 --> expectNullResponse(Sequence.one)
    )
  }

  abstract class AllQualifier(input: InputType) extends EventProcedure(input) {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = events.AllQualifierSteps
  }

  // qualifier 0x07, or 0x08 with twoByteCount
  abstract class CountQualifier(input: InputType, twoByteCount: Boolean) extends EventProcedure(input) {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] =
      events.CountQualifierSteps(options.get(input.numEventsGenerated), twoByteCount)
  }

  abstract class NoConfirm(input: InputType) extends EventProcedure(input) {
    // where a procedure's document differs from the others'
    protected def notes: List[LabeledStep] = Nil

    override protected def steps(options: OptionSource): Iterable[LabeledStep] =
      notes ++ events.NoConfirmSteps(options.get(OutstationOptions.appConfirmTimeout))
  }

  abstract class AllQualifierWithoutTime(input: InputType) extends EventProcedure(input) {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = events.AllQualifierWithoutTimeSteps
  }

  abstract class CountQualifierWithoutTime(input: InputType, twoByteCount: Boolean) extends EventProcedure(input) {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] =
      events.CountQualifierWithoutTimeSteps(options.get(input.numEventsGenerated), twoByteCount)
  }

  // runs whether or not the DUT reports events with time: a DUT without them must reject the request
  abstract class AllQualifierWithTime(input: InputType) extends EventProcedure(input) {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] =
      events.AllQualifierWithTimeSteps(options.get(input.withTime))
  }

  abstract class CountQualifierWithTime(input: InputType, twoByteCount: Boolean)
      extends EventProcedure(input, List(input.withTime)) {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] =
      events.CountQualifierWithTimeSteps(options.get(input.numEventsGenerated), twoByteCount)
  }

  // runs whether or not the DUT reports events with relative time: a DUT without them must reject the request
  abstract class AllQualifierWithRelativeTime(input: InputType) extends EventProcedure(input) {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] =
      events.AllQualifierWithRelativeTimeSteps(options.get(input.withRelativeTime))
  }

  abstract class CountQualifierWithRelativeTime(input: InputType, twoByteCount: Boolean)
      extends EventProcedure(input, List(input.withRelativeTime)) {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] =
      events.CountQualifierWithRelativeTimeSteps(options.get(input.numEventsGenerated), twoByteCount)
  }

  abstract class RelativeTimeLongInterval(input: InputType)
      extends EventProcedure(input, List(input.withRelativeTime)) {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] =
      events.AllQualifierWithRelativeTimeLongIntervalSteps
  }
}
