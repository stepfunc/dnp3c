// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.InputEventProcedures.NoEvents
import io.stepfunc.dnp3c.conformance.s8.helpers.{ InputType, Validations }
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.{ AutoConfirm, MessageValidation, Requests }

/**
 * 3.1r1's 8.14 tests, for binary inputs, and its 8.22 tests, which are the same tests for double-bit inputs. Each
 * checks the events it reads against 3.1r1's rules for event reporting ([[EventRules]]).
 */
object InputEvents {

  // the device's events can be read: a subset level 1 device may answer a read of them with an error instead
  private def readable(input: InputType): List[OptionCondition] =
    input.support.withEvents :+ OptionCondition(input.support.eventReads)

  sealed abstract class EventTest(val input: InputType) extends ProcedureDefinition {
    override protected def requiredOptions: List[OptionCondition] = readable(input)

    import input._

    // an event response: the events the plugin generated, a request for confirmation, the rules, and the flags
    protected def events(events: MessageValidation, requested: Variation, options: OptionSource): MessageValidation =
      Validations.eventResponse(events, EventRules.consistent(input, requested, options))

    // steps 1 to 3: read the events left and confirm them; a response with events must ask for confirmation
    protected def empty(seq: Sequence): List[LabeledStep] = List(
      1 --> send(Requests.readAllObjects(seq, anyVariation)),
      (2 to 3) --> expectEventsEmptied(seq)
    )
  }

  /** Qualifier 0x06 (8.14.2-3): every event in one response */
  final class AllQualifier(input: InputType) extends EventTest(input) {
    import input._
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = List(
      1 --> send(Requests.readAllObjects(Sequence.one, anyVariation)),
      2 --> expectEventsEmptied(Sequence.one),
      3 --> send(Requests.readAllObjects(Sequence.two, anyVariation)),
      4 --> expectNullResponse(Sequence.two),
      5 --> generateEvents,
      6 --> send(Requests.readAllObjects(Sequence.three, anyVariation)),
      (7 to 12) -->
        expectSingleResponse(Sequence.three, AutoConfirm.Yes, events(verifyLatestEvents, anyVariation, options)),
      13 --> send(Requests.readAllObjects(Sequence.four, anyVariation)),
      14 --> expectNullResponse(Sequence.four)
    )
  }

  /** Qualifier 0x07 (8.14.2-4), or 0x08 with twoByteCount (8.14.2-5): the events a few at a time */
  final class CountQualifier(input: InputType, twoByteCount: Boolean) extends EventTest(input) {
    import input._
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val count = options.get(numEventsGenerated)
      empty(Sequence.one) ++ List(
        4 --> generateExactEvents(count),
        5 --> send(Requests.readCount(Sequence.two, anyVariation, 1, twoByteCount)),
        (6 to 11) -->
          expectSingleResponse(Sequence.two, AutoConfirm.Yes, events(verifyFirstEvent, anyVariation, options)),
        12 --> send(Requests.readCount(Sequence.three, anyVariation, count - 1, twoByteCount)),
        (13 to 15) -->
          expectSingleResponse(Sequence.three, AutoConfirm.Yes, events(verifyRestEvents, anyVariation, options))
      )
    }
  }

  /** Without time, qualifier 0x06 (8.14.2-7) */
  final class AllQualifierWithoutTime(input: InputType) extends EventTest(input) {
    import input._
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = empty(Sequence.one) ++ List(
      4 --> generateEvents,
      5 --> send(Requests.readAllObjects(Sequence.two, noTimeVariation)),
      (6 to 10) -->
        expectSingleResponse(Sequence.two, AutoConfirm.Yes, events(verifyLatestEvents, noTimeVariation, options)),
      11 --> send(Requests.readAllObjects(Sequence.three, anyVariation)),
      12 --> expectNullResponse(Sequence.three)
    )
  }

  /** Without time, qualifier 0x07 (8.14.2-8), or 0x08 with twoByteCount (8.14.2-9) */
  final class CountQualifierWithoutTime(input: InputType, twoByteCount: Boolean) extends EventTest(input) {
    import input._
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val count = options.get(numEventsGenerated)
      empty(Sequence.one) ++ List(
        4 --> generateExactEvents(count),
        5 --> send(Requests.readCount(Sequence.two, noTimeVariation, 1, twoByteCount)),
        (6 to 12) -->
          expectSingleResponse(Sequence.two, AutoConfirm.Yes, events(verifyFirstEvent, noTimeVariation, options)),
        13 --> send(Requests.readCount(Sequence.three, noTimeVariation, count - 1, twoByteCount)),
        (14 to 16) -->
          expectSingleResponse(Sequence.three, AutoConfirm.Yes, events(verifyRestEvents, noTimeVariation, options)),
        17 --> send(Requests.readAllObjects(Sequence.four, anyVariation)),
        18 --> expectNullResponse(Sequence.four)
      )
    }
  }

  /**
   * A device without the input's events (8.14.2-2): as 2.7's, but only for a device whose events can be read, since a
   * subset level 1 device that can't read them answers with an error, which 8.14.2-1 tests
   */
  final class WithoutEvents(input: InputType) extends NoEvents(input) {
    override protected def requirement: Requirement =
      Requirement.all(input.support.withoutEvents, OptionCondition(input.support.eventReads))
  }

  import InputType.{ BinaryInput, DoubleBitBinaryInput }

  val binaryWithoutEvents = new WithoutEvents(BinaryInput)
  val binaryAll = new AllQualifier(BinaryInput)
  val binaryCount07 = new CountQualifier(BinaryInput, twoByteCount = false)
  val binaryCount08 = new CountQualifier(BinaryInput, twoByteCount = true)
  val binaryAllWithoutTime = new AllQualifierWithoutTime(BinaryInput)
  val binaryCount07WithoutTime = new CountQualifierWithoutTime(BinaryInput, twoByteCount = false)
  val binaryCount08WithoutTime = new CountQualifierWithoutTime(BinaryInput, twoByteCount = true)

  val doubleBitWithoutEvents = new WithoutEvents(DoubleBitBinaryInput)
  val doubleBitAll = new AllQualifier(DoubleBitBinaryInput)
  val doubleBitCount07 = new CountQualifier(DoubleBitBinaryInput, twoByteCount = false)
  val doubleBitCount08 = new CountQualifier(DoubleBitBinaryInput, twoByteCount = true)
  val doubleBitAllWithoutTime = new AllQualifierWithoutTime(DoubleBitBinaryInput)
  val doubleBitCount07WithoutTime = new CountQualifierWithoutTime(DoubleBitBinaryInput, twoByteCount = false)
  val doubleBitCount08WithoutTime = new CountQualifierWithoutTime(DoubleBitBinaryInput, twoByteCount = true)
}
