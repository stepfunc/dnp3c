// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.{ Inputs, Outputs }
import io.stepfunc.dnp3c.conformance.s8.helpers.GenericSteps._
import io.stepfunc.dnp3c.conformance.s8.helpers.Validations._
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects._
import io.stepfunc.dnp3c.protocol.parsing.{ UInt16LE, UInt8 }

/**
 * 3.1r1's 8.23, Specific Point Ranges: reads of a point type with a start/stop range (qualifier 0x00 or 0x01), which
 * subset level 3 requires and levels 1 and 2 may answer with IIN2.0, 2.1 or 2.2 instead (supports.rangeReads). Each
 * point type has three tests: a read of a device without the points, which must be an error; a read of points it has,
 * which must report those points and only those; and a read past its highest index, which must set IIN2.2.
 *
 * The tests need the indices of the DUT's points, which dnp3c finds by reading all of them (qualifier 0x06) first.
 */
object PointRanges {

  /**
   * @param name the points, in prose
   * @param anyVariation variation 0 of the type's static group
   * @param reads whether the DUT can read the points at all, for the inputs a level 1 device may not
   * @param secondAndThird whether the valid request, given three consecutive points, reads the second and third (binary
   *   inputs), not the first and second
   * @param escapeStep whether the test without the points has a step 3 of its own for a level 1 or 2 device without
   *   range reads (frozen counters, analog inputs, analog output status), which moves the later steps on
   */
  final case class RangedPoints(
      name: String,
      anyVariation: Variation,
      points: BooleanOption,
      disabling: BooleanOption,
      reads: Option[BooleanOption],
      uninstall: TestStep,
      secondAndThird: Boolean,
      escapeStep: Boolean) {
    def group: Group = anyVariation.group
  }

  private val levelOneOrTwoErrors = IIN.FUNC_NOT_SUPPORTED | IIN.OBJECT_UNKNOWN | IIN.PARAM_ERROR

  private def rangeRead(seq: Sequence, variation: Variation, twoByte: Boolean, start: Int, stop: Int): Message =
    Requests.readHeaders(
      seq,
      if (twoByte) TwoByteStartStopHeader(variation, UInt16LE(start), UInt16LE(stop), FieldHeaderPayload.empty)
      else OneByteStartStopHeader(variation, UInt8(start.toShort), UInt8(stop.toShort), FieldHeaderPayload.empty)
    )

  private def describedRead(seq: Sequence, variation: Variation, twoByte: Boolean, range: String) =
    describe(
      s"Read $variation with a ${if (twoByte) "16-bit (qualifier 0x01)" else "8-bit (qualifier 0x00)"} start/stop " +
        s"range, $range, w/ $seq")

  // the indices of the DUT's points of the type, which a read of all of them finds
  private final class Indices(t: RangedPoints) {
    private var found: Option[List[Int]] = None
    def get: List[Int] = found.filter(_.nonEmpty).getOrElse(throw new Exception(s"The DUT reported no ${t.name}"))
    val find: TestStep = send(Requests.readAllObjects(Sequence.zero, t.anyVariation)) +
      expectAtLeastOneResponse(
        Sequence.zero,
        MessageValidation.check(s"Note the indices of the ${t.name} reported") { message =>
          found = Some(message.measurements(PointType.Static).map(_.idx).distinct.sorted.toList)
        }.acrossFragments
      )
  }

  // across the fragments: only objects of the group, in start/stop ranges, reporting exactly the points of indices
  private def onlyPoints(t: RangedPoints, expected: => Set[Int], which: String): ResponseValidation =
    new ResponseValidation {
      def description: List[Comment] = List(
        s"Verify that the response uses an 8-bit (0x00) or a 16-bit (0x01) start/stop range, for $which")
      def validate(reporter: ProcedureReporter, responses: List[Message]): List[DeferredCheck] = {
        val headers = responses.flatMap(_.headers)
        headers.find(h =>
          h.variation.group != t.group ||
            (h.qualifier != Qualifier.ONE_BYTE_START_STOP && h.qualifier != Qualifier.TWO_BYTE_START_STOP)).foreach(h =>
          throw new Exception(s"Unexpected ${h.variation} with qualifier ${h.qualifier}"))
        val reported = responses.flatMap(_.measurements(PointType.Static).map(_.idx)).toSet
        if (reported != expected)
          throw new Exception(
            s"Expected the points ${expected.toList.sorted.mkString(", ")}, but the response has " +
              reported.toList.sorted.mkString(", "))
        Nil
      }
    }

  /** x.1: a device without the points, or with them disabled, answers range reads with an error */
  def withoutPoints(t: RangedPoints): ProcedureDefinition = new ProcedureDefinition {
    override protected def requirement: Requirement =
      Requirement.anyOf(!t.points, Requirement.all(OptionCondition(t.points), OptionCondition(t.disabling)))

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val has = options.get(t.points)
      // frozen counters, analog inputs and analog output status: a level 1 or 2 device without range reads leaves the
      // type's tests at step 3
      val leaves = t.escapeStep && !options.get(SupportOptions.rangeReads)
      val e = if (t.escapeStep) 1 else 0
      val error = MessageValidation.check(s"Verify that the response is an error response, with an error IIN bit set") {
        message =>
          if (message.iin.forall(i => (i & IIN.errors) == IIN.empty)) throw new Exception("Expected an error IIN bit")
      }
      val restore = PluginSteps.reinstallPoints
      // once step 1 has uninstalled the points, they're reinstalled after the last step, or after one that fails
      def guarded(step: TestStep, last: Boolean = false): TestStep = new TestStep {
        def description: List[Comment] = step.description ++ (if (has && last) restore.description else Nil)
        def run(driver: TestDriver): List[DeferredCheck] =
          if (!has) step.run(driver)
          else if (last)
            try step.run(driver)
            finally restore.run(driver)
          else
            try step.run(driver)
            catch {
              case ex: Throwable =>
                restore.run(driver)
                throw ex
            }
      }
      def read(seq: Sequence, twoByte: Boolean): TestStep =
        describedRead(seq, t.anyVariation, twoByte, "0 to 0") + send(rangeRead(seq, t.anyVariation, twoByte, 0, 0))
      val first = 1 --> (if (has) t.uninstall else describe(s"The DUT has no ${t.name}"))
      if (leaves)
        List(
          first,
          2 --> guarded(read(Sequence.one, twoByte = false)),
          3 --> guarded(expectSingleResponse(Sequence.one, unsupported), last = true),
          (4 to 7) --> describe(
            s"The DUT doesn't read with start/stop ranges: no further steps, or tests of ${t.name}, apply")
        )
      else
        List(
          first,
          2 --> guarded(read(Sequence.one, twoByte = false)),
          (if (t.escapeStep) Label(3, 4) else Label(3)) --> guarded(expectSingleResponse(Sequence.one, error)),
          4 + e --> guarded(read(Sequence.two, twoByte = true)),
          5 + e --> guarded(expectSingleResponse(Sequence.two, error), last = true),
          6 + e --> describe(s"The ${t.name} are back, if they were uninstalled")
        )
    }
  }

  // the DUT reads the points; and for a type whose test without points leaves the type's tests where the DUT has no
  // range reads, it has them
  private def readsPoints(t: RangedPoints): Requirement = Requirement.all(
    (OptionCondition(t.points) :: t.reads.map(OptionCondition(_)).toList ++
      Option.when(t.escapeStep)(OptionCondition(SupportOptions.rangeReads)).toList)*)

  // a level 1 or 2 device without range reads answers with IIN2.0, 2.1 or 2.2
  private val unsupported = isEmpty && containingAnyOf(levelOneOrTwoErrors)

  /** x.2: a range of points the DUT has gets those points, and only those */
  def validRequest(t: RangedPoints): ProcedureDefinition = new ProcedureDefinition {
    override protected def requirement: Requirement = readsPoints(t)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val indices = new Indices(t)
      // of the first three consecutive points: the second and third, or the first and second; else the first alone;
      // of those an 8-bit range can request, as step 1's does
      def range: (Int, Int) = {
        val all = indices.get.filter(_ <= 0xFF)
        if (all.isEmpty)
          throw new Exception(s"The DUT has no ${t.name} at an index an 8-bit start/stop range can request")
        all.sliding(3).find(w => w.size == 3 && w(1) == w(0) + 1 && w(2) == w(1) + 1) match {
          case Some(w) => if (t.secondAndThird) (w(1), w(2)) else (w(0), w(1))
          case None => (all.head, all.head)
        }
      }
      val what =
        if (t.secondAndThird) "the 2nd and 3rd of the first three consecutive points, else the first point alone"
        else "the first and the next of the first three consecutive points, else the first point alone"
      def read(seq: Sequence, twoByte: Boolean): TestStep = new TestStep {
        def description: List[Comment] = describedRead(seq, t.anyVariation, twoByte, what).description
        def run(driver: TestDriver): List[DeferredCheck] = {
          val (start, stop) = range
          send(rangeRead(seq, t.anyVariation, twoByte, start, stop)).run(driver)
        }
      }
      val find = describe(s"Before step 1, read all of the DUT's ${t.name} to find their indices") + indices.find
      if (options.get(SupportOptions.rangeReads)) {
        def expect(seq: Sequence): TestStep =
          expectAtLeastOneResponse(seq, onlyPoints(t, { val (a, b) = range; (a to b).toSet }, "the points requested"))
        List(
          1 --> (find + read(Sequence.one, twoByte = false)),
          (2 to 4) --> expect(Sequence.one),
          5 --> read(Sequence.two, twoByte = true),
          (6 to 8) --> expect(Sequence.two))
      } else
        List(
          1 --> (find + read(Sequence.one, twoByte = false)),
          2 --> expectSingleResponse(Sequence.one, unsupported),
          (3 to 4) --> describe("The DUT doesn't read with start/stop ranges: skip to step 5"),
          5 --> read(Sequence.two, twoByte = true),
          6 --> expectSingleResponse(Sequence.two, unsupported),
          (7 to 8) --> describe("The DUT doesn't read with start/stop ranges: no further steps apply")
        )
    }
  }

  /** x.3: a range from the highest index past it gets IIN2.2, and at most the point that exists */
  def outOfRange(t: RangedPoints): ProcedureDefinition = new ProcedureDefinition {
    override protected def requirement: Requirement = readsPoints(t)

    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val indices = new Indices(t)
      def highest: Int = indices.get.max
      val read = new TestStep {
        def description: List[Comment] = describe(
          s"Read ${t.anyVariation} with a start/stop range from the highest index of the ${t.name} to one past it, " +
            "8-bit (qualifier 0x00) where it fits, else 16-bit (0x01)").description
        def run(driver: TestDriver): List[DeferredCheck] = {
          if (highest >= 0xFFFF) throw new Exception(s"The highest index of the ${t.name} is 65535: none is past it")
          send(rangeRead(Sequence.one, t.anyVariation, twoByte = highest + 1 > 0xFF, highest, highest + 1)).run(driver)
        }
      }
      val expected = containing(IIN.PARAM_ERROR) && MessageValidation.check(
        s"Verify that the response reports nothing but the ${t.name} at the highest index, if anything") { message =>
        message.headers.find(h => h.variation.group != t.group).foreach(h =>
          throw new Exception(s"Unexpected ${h.variation}"))
        val reported = message.measurements(PointType.Static).map(_.idx).toSet
        if (!reported.subsetOf(Set(highest)))
          throw new Exception(s"Unexpected points ${reported.toList.sorted.mkString(", ")}")
      }
      List(
        1 --> (describe(s"Before step 1, read all of the DUT's ${t.name} to find their indices") + indices.find + read),
        2 --> expectSingleResponse(Sequence.one, expected)
      )
    }
  }

  val binaryInputs = RangedPoints(
    "binary inputs",
    Group1Var0,
    Inputs.binary.points,
    Inputs.binary.disabling,
    Some(Inputs.binary.reads),
    PluginSteps.uninstallBinaryInputs,
    secondAndThird = true,
    escapeStep = false
  )
  val binaryOutputStatus = RangedPoints(
    "binary output status points",
    Group10Var0,
    Outputs.binary.statusPoints,
    Outputs.binary.disabling,
    None,
    PluginSteps.uninstallBinaryOutputs,
    secondAndThird = false,
    escapeStep = false
  )
  val counters = RangedPoints(
    "counters",
    Group20Var0,
    Inputs.counter.points,
    Inputs.counter.disabling,
    Some(Inputs.counter.reads),
    PluginSteps.uninstallCounterInputs,
    secondAndThird = false,
    escapeStep = false
  )
  val frozenCounters = RangedPoints(
    "frozen counters",
    Group21Var0,
    Inputs.frozenCounter.points,
    Inputs.frozenCounter.disabling,
    Some(Inputs.frozenCounter.reads),
    PluginSteps.uninstallFrozenCounters,
    secondAndThird = false,
    escapeStep = true
  )
  val analogInputs = RangedPoints(
    "analog inputs",
    Group30Var0,
    Inputs.analog.points,
    Inputs.analog.disabling,
    Some(Inputs.analog.reads),
    PluginSteps.uninstallAnalogInputs,
    secondAndThird = false,
    escapeStep = true
  )
  val analogOutputStatus = RangedPoints(
    "analog output status points",
    Group40Var0,
    Outputs.analog.statusPoints,
    Outputs.analog.disabling,
    None,
    PluginSteps.uninstallAnalogOutputs,
    secondAndThird = false,
    escapeStep = true
  )

  val types: List[RangedPoints] =
    List(binaryInputs, binaryOutputStatus, counters, frozenCounters, analogInputs, analogOutputStatus)
}
