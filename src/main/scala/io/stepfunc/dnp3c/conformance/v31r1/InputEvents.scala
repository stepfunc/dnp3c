// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.InputEventProcedures.NoEvents
import io.stepfunc.dnp3c.conformance.s8.helpers.{ InputType, Validations }
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.{ AutoConfirm, Comment, DeferredCheck, MessageValidation, Requests, TestDriver }

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

  // the note on a device that answers a read of events with time with IIN2.1
  private val compliantWith2012Only = "the DUT answers a read of events with time with IIN2.1: it complies with IEEE " +
    "1815-2012, but not with IEEE 1815-202x"

  /**
   * With time, qualifier 0x06 (8.14.2-10). A device without events with time may answer with a Null Error Response
   * with IIN2.1, if it's configured to: that passes, noted as compliant with 1815-2012 but not 1815-202x, and its other
   * with-time tests are skipped. Otherwise the events, with time or not, are reported as the rules require.
   */
  final class AllQualifierWithTime(input: InputType) extends EventTest(input) {
    import input._
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val start = empty(Sequence.one) ++ List(
        4 --> generateEvents,
        5 --> send(Requests.readAllObjects(Sequence.two, absoluteTimeVariation)))
      if (options.get(withTimeRejected))
        start :+
          (6 -->
            // the step asks for IIN2.1; other error bits aren't ruled out
            (expectSingleResponse(Sequence.two, Validations.isEmpty && Validations.containing(IIN.OBJECT_UNKNOWN)) +
              new TestStep {
                def description = List(Comment(s"Note the result: $compliantWith2012Only"))
                def run(driver: TestDriver): List[DeferredCheck] = { driver.reporter.note(compliantWith2012Only); Nil }
              }))
      else
        start ++ List(
          (7 to 12) --> expectSingleResponse(
            Sequence.two,
            AutoConfirm.Yes,
            events(verifyLatestEvents, absoluteTimeVariation, options)),
          13 --> send(Requests.readAllObjects(Sequence.three, anyVariation)),
          14 --> expectNullResponse(Sequence.three)
        )
    }
  }

  /**
   * With time, qualifier 0x07 (8.14.2-11), or 0x08 with twoByteCount (8.14.2-12): skipped for a device that answers a
   * read of events with time with IIN2.1, which 8.14.2-10 tests
   */
  final class CountQualifierWithTime(input: InputType, twoByteCount: Boolean) extends EventTest(input) {
    import input._
    override protected def requiredOptions: List[OptionCondition] = super.requiredOptions :+ !withTimeRejected
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val count = options.get(numEventsGenerated)
      // 0x08's steps are numbered one lower after its first response's checks begin: it has no step for the count
      val shift = if (twoByteCount) 1 else 0
      def at(n: Int): Int = n - shift
      empty(Sequence.one) ++ List(
        4 --> generateExactEvents(count),
        5 --> send(Requests.readCount(Sequence.two, absoluteTimeVariation, 1, twoByteCount)),
        (6 to at(12)) -->
          expectSingleResponse(Sequence.two, AutoConfirm.Yes, events(verifyFirstEvent, absoluteTimeVariation, options)),
        at(13) --> send(Requests.readCount(Sequence.three, absoluteTimeVariation, count - 1, twoByteCount)),
        (at(14) to at(16)) -->
          expectSingleResponse(
            Sequence.three,
            AutoConfirm.Yes,
            events(verifyRestEvents, absoluteTimeVariation, options)),
        at(17) --> send(Requests.readAllObjects(Sequence.four, anyVariation)),
        at(18) --> expectNullResponse(Sequence.four)
      )
    }
  }

  /**
   * Without confirm (8.14.2-6): the response to a read isn't confirmed, so the events stay; read again, the device
   * answers with the same objects under the new sequence number, and doesn't retransmit either; then new events join
   * them in the next response, which is confirmed
   */
  final class NoConfirm(input: InputType) extends EventTest(input) {
    import input._
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val retry = options.get(io.stepfunc.dnp3c.conformance.api.options.OutstationOptions.appConfirmTimeout)
      var first: Option[List[ObjectHeader]] = None
      val keep = MessageValidation.check("Keep the response's objects, to compare the next read's with") { m =>
        first = Some(m.headers)
      }
      val same = MessageValidation.check("Verify that the response carries the same objects as the previous one") { m =>
        if (!first.contains(m.headers))
          throw new Exception("The response doesn't carry the same objects as the response it repeats")
      }
      empty(Sequence.one) ++ List(
        4 --> generateEvents,
        5 --> send(Requests.readAllObjects(Sequence.two, anyVariation)),
        (6 to 10) -->
          expectSingleResponse(Sequence.two, AutoConfirm.No, events(verifyLatestEvents, anyVariation, options) && keep),
        11 --> describe("Withhold the application layer confirmation for this response"),
        (12 to 13) --> expectNoRetransmission(retry),
        14 --> send(Requests.readAllObjects(Sequence.three, anyVariation)),
        15 --> expectSingleResponse(Sequence.three, AutoConfirm.No, same),
        16 --> describe("Withhold the application layer confirmation for this response too"),
        (17 to 18) --> expectNoRetransmission(retry),
        19 --> generateEvents,
        20 --> send(Requests.readAllObjects(Sequence.four, anyVariation)),
        (21 to 23) -->
          expectSingleResponse(Sequence.four, AutoConfirm.Yes, events(verifyOldAndNewEvents, anyVariation, options)),
        24 --> send(Requests.readAllObjects(Sequence.five, anyVariation)),
        25 --> expectNullResponse(Sequence.five)
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
  val binaryNoConfirm = new NoConfirm(BinaryInput)
  val binaryAllWithTime = new AllQualifierWithTime(BinaryInput)
  val binaryCount07WithTime = new CountQualifierWithTime(BinaryInput, twoByteCount = false)
  val binaryCount08WithTime = new CountQualifierWithTime(BinaryInput, twoByteCount = true)

  val doubleBitWithoutEvents = new WithoutEvents(DoubleBitBinaryInput)
  val doubleBitAll = new AllQualifier(DoubleBitBinaryInput)
  val doubleBitCount07 = new CountQualifier(DoubleBitBinaryInput, twoByteCount = false)
  val doubleBitCount08 = new CountQualifier(DoubleBitBinaryInput, twoByteCount = true)
  val doubleBitAllWithoutTime = new AllQualifierWithoutTime(DoubleBitBinaryInput)
  val doubleBitCount07WithoutTime = new CountQualifierWithoutTime(DoubleBitBinaryInput, twoByteCount = false)
  val doubleBitCount08WithoutTime = new CountQualifierWithoutTime(DoubleBitBinaryInput, twoByteCount = true)
  val doubleBitNoConfirm = new NoConfirm(DoubleBitBinaryInput)
  val doubleBitAllWithTime = new AllQualifierWithTime(DoubleBitBinaryInput)
  val doubleBitCount07WithTime = new CountQualifierWithTime(DoubleBitBinaryInput, twoByteCount = false)
  val doubleBitCount08WithTime = new CountQualifierWithTime(DoubleBitBinaryInput, twoByteCount = true)
}
