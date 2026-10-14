// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.InputEventProcedures.NoEvents
import io.stepfunc.dnp3c.conformance.common.{ InputType, Validations }
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

    // the Device Profile's default variation, which reads of variation 0 are checked against
    override def configurationProblems(options: OptionSource): List[ConfigProblem] =
      Option.when(options.get(input.defaultEventVariation).isEmpty)(
        ConfigProblem(
          input.defaultEventVariation.id,
          "",
          s"required by 3.1r1's ${input.name} event tests, to check reads of ${input.anyVariation} against the Device " +
            "Profile's default"
        )).toList

    import input._

    // an event response: the events the plugin generated, a request for confirmation, the rules, and the flags
    protected def events(
        events: MessageValidation,
        requested: Variation,
        options: OptionSource,
        clock: => EventRules.Clock = EventRules.Clock.Unknown): MessageValidation =
      Validations.eventResponse(events, EventRules.consistent(input, requested, options, clock))

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
              noting(compliantWith2012Only)))
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

  // a step that runs only if `condition` holds when it's reached, e.g. one an earlier answer of the device skips
  private def when(condition: => Boolean, what: String)(step: TestStep): TestStep = new TestStep {
    def description: List[Comment] = step.description :+ Comment(s"(Only if $what)")
    def run(driver: TestDriver): List[DeferredCheck] =
      if (condition) step.run(driver)
      else {
        driver.reporter.info(s"Skipped: not $what")
        Nil
      }
  }

  // the note on a device that answers a read of events with relative time with IIN2.1
  private[v31r1] val relativeCompliantWith2012Only =
    "the DUT answers a read of events with relative time with IIN2.1: it " +
      "complies with IEEE 1815-2012, but not with IEEE 1815-202x"

  /**
   * With relative time, qualifier 0x06 (8.14.2-13), in three phases: after a restart, while the clock is
   * unsynchronized; after a time write, synchronized; and after the synchronization expires. A device without events
   * with relative time may answer with IIN2.1, if it's configured to: that passes, noted as compliant with 1815-2012
   * but not 1815-202x, and the rest of 8.14 and 8.15 are skipped.
   */
  final class AllQualifierWithRelativeTime(input: InputType) extends EventTest(input) {
    import input._
    import io.stepfunc.dnp3c.conformance.api.options.TimeSyncOptions
    import EventRules.Clock

    // 8.14.2-13 waits for the clock to go unsynchronized: how long is the manufacturer's to say
    override def configurationProblems(options: OptionSource): List[ConfigProblem] =
      super.configurationProblems(options) ++ Option.when(
        options.get(withRelativeTime) && options.get(TimeSyncOptions.supportSynchronizingTimeFromMaster) &&
          options.get(TimeSyncOptions.timeToUnsynchronizedMs).isEmpty)(
        ConfigProblem(
          TimeSyncOptions.timeToUnsynchronizedMs.id,
          "",
          "required by 3.1r1's relative time tests, which wait for the clock to go unsynchronized"))

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val start = List(
        1 --> PluginSteps.cyclePower,
        2 --> send(Requests.readAllObjects(Sequence.zero, anyVariation)))
      if (options.get(withRelativeTimeRejected))
        start ++ List(
          (3 to 4) --> expectEventsEmptied(Sequence.zero),
          6 --> generateEvents,
          7 -->
            (send(Requests.readAllObjects(Sequence.one, relativeTimeVariation)) +
              expectSingleResponse(Sequence.one, Validations.isEmpty && Validations.containing(IIN.OBJECT_UNKNOWN)) +
              noting(relativeCompliantWith2012Only)),
          (8 to 38) --> describe("Skipped, with the rest of 8.14 and all of 8.15")
        )
      else {
        // whether the DUT asked for the time after the restart: the first phase needs it
        var needTime = false
        val noteNeedTime = MessageValidation.check("Note whether IIN1.4 (NEED_TIME) is set") { m =>
          needTime = needTime || m.iin.exists(i => (i & IIN.NEED_TIME) == IIN.NEED_TIME)
        }
        val unsynchronized = "IIN1.4 was set in step 3's response"
        // the master can write the device's time, and the device asks for it (sets IIN1.4)
        val synchronize = options.get(TimeSyncOptions.supportSynchronizingTimeFromMaster)
        val written = unsynchronized + ", and the master can write the time"
        // the third phase: a device that reports relative time and asks for the time
        val expires = options.get(withRelativeTime) && synchronize
        val expiry = "the DUT reports events with relative time and asks for the time"
        // after step 17's time write the clock is synchronized; with no write, its state isn't known
        def afterSync: Clock = if (synchronize && needTime) Clock.Synchronized else Clock.Unknown
        start ++ List(
          (3 to 4) -->
            expectAtLeastOneResponse(Sequence.zero, Validations.verifyConfirmRequestedIfNotNull && noteNeedTime),
          5 --> describe("If IIN1.4 wasn't set in step 3's response, skip to step 19"),
          6 --> when(needTime, unsynchronized)(generateEvents),
          7 --> when(needTime, unsynchronized)(send(Requests.readAllObjects(Sequence.one, relativeTimeVariation))),
          (8 to 14) --> when(needTime, unsynchronized)(expectSingleResponse(
            Sequence.one,
            AutoConfirm.Yes,
            events(verifyLatestEvents, relativeTimeVariation, options, Clock.Unsynchronized))),
          15 --> when(needTime, unsynchronized)(send(Requests.readAllObjects(Sequence.two, anyVariation))),
          16 --> when(needTime, unsynchronized)(expectNullResponse(Sequence.two)),
          // skipped with steps 6 to 16, as step 5 says: a device that didn't ask for the time keeps its clock
          17 --> when(synchronize && needTime, written)(
            writeCurrentTime(Sequence.three, options.get(TimeSyncOptions.timeWriteLatencyMs))),
          18 --> when(synchronize && needTime, written)(
            expectSingleResponse(Sequence.three, Validations.isNull && Validations.without(IIN.NEED_TIME))),
          19 --> generateEvents,
          20 --> send(Requests.readAllObjects(Sequence.four, relativeTimeVariation)),
          (21 to 28) --> expectSingleResponse(
            Sequence.four,
            AutoConfirm.Yes,
            events(verifyLatestEvents, relativeTimeVariation, options, afterSync)),
          29 --> send(Requests.readAllObjects(Sequence.five, anyVariation)),
          30 --> expectNullResponse(Sequence.five),
          31 --> describe(
            "If the DUT doesn't report events with relative time, or never sets IIN1.4, skip the rest of this test"),
          32 --> when(expires, expiry)(delay(options.get(TimeSyncOptions.maxTimeFromResetToIIN14Ms))),
          33 --> when(expires, expiry)(delay(options.get(TimeSyncOptions.timeToUnsynchronizedMs).getOrElse(0))),
          34 --> when(expires, expiry)(generateEvents),
          35 --> when(expires, expiry)(send(Requests.readAllObjects(Sequence(6), relativeTimeVariation))),
          (36 to 38) --> when(expires, expiry)(expectSingleResponse(
            Sequence(6),
            AutoConfirm.Yes,
            events(verifyLatestEvents, relativeTimeVariation, options, Clock.Unsynchronized)))
        )
      }
    }
  }

  // the input's events with relative time can be read: not a device that answers such a read with IIN2.1
  private def relativeReadable(input: InputType): List[OptionCondition] = !input.withRelativeTimeRejected :: Nil

  /** With relative time, qualifier 0x07 (8.14.2-14), or 0x08 with twoByteCount (8.14.2-15) */
  final class CountQualifierWithRelativeTime(input: InputType, twoByteCount: Boolean) extends EventTest(input) {
    import input._
    override protected def requiredOptions: List[OptionCondition] = super.requiredOptions ++ relativeReadable(input)
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val count = options.get(numEventsGenerated)
      empty(Sequence.one) ++ List(
        4 --> generateExactEvents(count),
        5 --> send(Requests.readCount(Sequence.two, relativeTimeVariation, 1, twoByteCount)),
        (6 to 12) -->
          expectSingleResponse(Sequence.two, AutoConfirm.Yes, events(verifyFirstEvent, relativeTimeVariation, options)),
        13 --> send(Requests.readCount(Sequence.three, relativeTimeVariation, count - 1, twoByteCount)),
        (14 to 16) -->
          expectSingleResponse(
            Sequence.three,
            AutoConfirm.Yes,
            events(verifyRestEvents, relativeTimeVariation, options)),
        17 --> send(Requests.readAllObjects(Sequence.four, anyVariation)),
        18 --> expectNullResponse(Sequence.four)
      )
    }
  }

  /**
   * With relative time, a long interval (8.14.2-16): events more than 0xFFFF ms apart need a CTO each. Only for a device
   * that reports events with relative time.
   */
  final class RelativeTimeLongInterval(input: InputType) extends EventTest(input) {
    import input._
    override protected def requiredOptions: List[OptionCondition] =
      super.requiredOptions ++ relativeReadable(input) :+ OptionCondition(withRelativeTime)
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val ctos: Set[Variation] =
        Set(io.stepfunc.dnp3c.dnp3.app.objects.Group51Var1, io.stepfunc.dnp3c.dnp3.app.objects.Group51Var2)
      val twoCtos = MessageValidation.check(
        "Verify that a CTO (g51v1 or g51v2) precedes each group of events more than 0xFFFF ms apart: at least two") {
        m =>
          if (m.headers.count(h => ctos.contains(h.variation)) < 2)
            throw new Exception("Expected at least two CTOs, for the events generated more than 0xFFFF ms apart")
      }
      List(
        1 --> describe("For a DUT that reports events with relative time"),
        2 --> send(Requests.readAllObjects(Sequence.one, anyVariation)),
        (3 to 4) --> expectEventsEmptied(Sequence.one),
        5 --> generateEvents,
        // "longer than 0xFFFF ms", with a margin for when the DUT detects and timestamps each batch
        6 --> delay(0xFFFF + 5000),
        7 --> generateEvents,
        8 --> send(Requests.readAllObjects(Sequence.two, relativeTimeVariation)),
        (9 to 16) --> expectSingleResponse(
          Sequence.two,
          AutoConfirm.Yes,
          Validations.eventResponse(
            verifyOldAndNewEvents,
            Validations.eventsWithCtos(List(relativeTimeVariation)) && twoCtos)),
        17 --> send(Requests.readAllObjects(Sequence.three, relativeTimeVariation)),
        18 --> expectNullResponse(Sequence.three)
      )
    }
  }

  /**
   * A subset level 1 device that can't read the input's events (8.14.2-1): it answers reads of variations 0 and 1 with a
   * Null Error Response with IIN2.0, 2.1 or 2.2 (2.1 preferred), which the result notes
   */
  final class LevelOneWithoutEventReads(input: InputType) extends ProcedureDefinition {
    import input._
    override protected def requirement: Requirement = !input.support.eventReads

    private val errors = IIN.FUNC_NOT_SUPPORTED | IIN.OBJECT_UNKNOWN | IIN.PARAM_ERROR

    // the response must be a Null Error Response; which error bit it set goes in the result
    private def rejected(seq: Sequence, read: Variation): TestStep = new TestStep {
      private val check = Validations.isEmpty && Validations.containingAnyOf(errors)
      def description: List[Comment] =
        check.description :+ Comment("Note the error IIN bits set (IIN2.1 is preferred)")
      def run(driver: TestDriver): List[DeferredCheck] = {
        var set = IIN.empty
        val noted = check && MessageValidation.check("") { m => set = m.iin.getOrElse(IIN.empty) & errors }
        val checks = expectSingleResponse(seq, noted).run(driver)
        driver.reporter.note(s"the DUT answers a read of $read with $set")
        checks
      }
    }

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = List(
      1 --> send(Requests.readAllObjects(Sequence.one, anyVariation)),
      2 --> rejected(Sequence.one, anyVariation),
      3 --> send(Requests.readAllObjects(Sequence.two, noTimeVariation)),
      4 --> rejected(Sequence.two, noTimeVariation),
      5 --> describe(s"No further ${input.name} event tests (or 8.15) apply to this DUT")
    )
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

  val binaryLevelOne = new LevelOneWithoutEventReads(BinaryInput)
  val binaryWithoutEvents = new WithoutEvents(BinaryInput)
  val binaryAll = new AllQualifier(BinaryInput)
  val binaryCount07 = new CountQualifier(BinaryInput, twoByteCount = false)
  val binaryCount08 = new CountQualifier(BinaryInput, twoByteCount = true)
  val binaryAllWithoutTime = new AllQualifierWithoutTime(BinaryInput)
  val binaryCount07WithoutTime = new CountQualifierWithoutTime(BinaryInput, twoByteCount = false)
  val binaryCount08WithoutTime = new CountQualifierWithoutTime(BinaryInput, twoByteCount = true)
  val binaryNoConfirm = new NoConfirm(BinaryInput)
  val binaryAllWithTime = new AllQualifierWithTime(BinaryInput)
  val binaryAllWithRelativeTime = new AllQualifierWithRelativeTime(BinaryInput)
  val binaryCount07WithRelativeTime = new CountQualifierWithRelativeTime(BinaryInput, twoByteCount = false)
  val binaryCount08WithRelativeTime = new CountQualifierWithRelativeTime(BinaryInput, twoByteCount = true)
  val binaryLongInterval = new RelativeTimeLongInterval(BinaryInput)
  val binaryCount07WithTime = new CountQualifierWithTime(BinaryInput, twoByteCount = false)
  val binaryCount08WithTime = new CountQualifierWithTime(BinaryInput, twoByteCount = true)

  val doubleBitLevelOne = new LevelOneWithoutEventReads(DoubleBitBinaryInput)
  val doubleBitWithoutEvents = new WithoutEvents(DoubleBitBinaryInput)
  val doubleBitAll = new AllQualifier(DoubleBitBinaryInput)
  val doubleBitCount07 = new CountQualifier(DoubleBitBinaryInput, twoByteCount = false)
  val doubleBitCount08 = new CountQualifier(DoubleBitBinaryInput, twoByteCount = true)
  val doubleBitAllWithoutTime = new AllQualifierWithoutTime(DoubleBitBinaryInput)
  val doubleBitCount07WithoutTime = new CountQualifierWithoutTime(DoubleBitBinaryInput, twoByteCount = false)
  val doubleBitCount08WithoutTime = new CountQualifierWithoutTime(DoubleBitBinaryInput, twoByteCount = true)
  val doubleBitNoConfirm = new NoConfirm(DoubleBitBinaryInput)
  val doubleBitAllWithTime = new AllQualifierWithTime(DoubleBitBinaryInput)
  val doubleBitAllWithRelativeTime = new AllQualifierWithRelativeTime(DoubleBitBinaryInput)
  val doubleBitCount07WithRelativeTime = new CountQualifierWithRelativeTime(DoubleBitBinaryInput, twoByteCount = false)
  val doubleBitCount08WithRelativeTime = new CountQualifierWithRelativeTime(DoubleBitBinaryInput, twoByteCount = true)
  val doubleBitLongInterval = new RelativeTimeLongInterval(DoubleBitBinaryInput)
  val doubleBitCount07WithTime = new CountQualifierWithTime(DoubleBitBinaryInput, twoByteCount = false)
  val doubleBitCount08WithTime = new CountQualifierWithTime(DoubleBitBinaryInput, twoByteCount = true)
}
