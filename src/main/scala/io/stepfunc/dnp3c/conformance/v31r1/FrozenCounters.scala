// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.CounterOptions
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.conformance.common.FrozenCounterSteps._
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.Validations
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects._

/**
 * 3.1r1's freeze tests, 8.16.2.2.3 to 8.16.2.2.6. A device with frozen counter events, which subset level 3 requires,
 * reads them with the frozen counters after each freeze: one request for g23 then g21, whose response must report the
 * events first, ask for confirmation, and give each point the same value in both. A device without them reads the
 * frozen counters alone, as in 2.7. The steps between the freezes are 2.7's, apart from their numbers.
 */
object FrozenCounters {

  // reads its frozen counter events with the frozen counters: a device that has them and, at level 1, can read them
  private def withEvents(options: OptionSource): Boolean =
    options.get(Inputs.frozenCounter.events) && options.get(Inputs.frozenCounter.eventReads)

  // a check of the frozen counters (g21) alone, of a response that also reports frozen counter events
  private def ofFrozenCounters(v: ResponseValidation): ResponseValidation = new ResponseValidation {
    def description: List[Comment] = v.description
    def validate(reporter: ProcedureReporter, responses: List[Message]): List[DeferredCheck] =
      v.validate(reporter, responses.map(m => m.copy(headers = m.headers.filter(_.variation.group == Group21))))
  }

  private val eventVariations: Set[Variation] = Set(Group23Var1, Group23Var2)
  private val frozenVariations: Set[Variation] = Set(Group21Var1, Group21Var2, Group21Var9, Group21Var10)
  private val eventQualifiers = Set(Qualifier.ONE_BYTE_COUNT_AND_INDEX, Qualifier.TWO_BYTE_COUNT_AND_INDEX)
  private val staticQualifiers = Set(Qualifier.ONE_BYTE_START_STOP, Qualifier.TWO_BYTE_START_STOP)

  // across the fragments, frozen counter events, then frozen counters, and nothing else
  private[v31r1] val eventsThenFrozenCounters: ResponseValidation = new ResponseValidation {
    def description: List[Comment] = List(
      "Verify that the response holds frozen counter events (g23v1 or g23v2, qualifier 0x17 or 0x28), followed by " +
        "frozen counters (g21v1, g21v2, g21v9 or g21v10, qualifier 0x00 or 0x01), and nothing else")
    def validate(reporter: ProcedureReporter, responses: List[Message]): List[DeferredCheck] = {
      val headers = responses.flatMap(_.headers)
      val (events, rest) = headers.span(h => eventVariations.contains(h.variation))
      events.find(h => !eventQualifiers.contains(h.qualifier)).foreach(h =>
        throw new Exception(s"Unexpected qualifier ${h.qualifier} for ${h.variation}"))
      rest.find(h => !frozenVariations.contains(h.variation) || !staticQualifiers.contains(h.qualifier)).foreach(h =>
        throw new Exception(s"Unexpected ${h.variation} with qualifier ${h.qualifier} after the frozen counter events"))
      if (events.isEmpty) throw new Exception("Expected frozen counter events (g23) first in the response")
      if (rest.isEmpty) throw new Exception("Expected frozen counters (g21) after the frozen counter events")
      Nil
    }
  }

  /**
   * Every point is reported in both objects, and, if sameValues, each point's latest event has its frozen value
   */
  private[v31r1] def bothObjects(sameValues: Boolean): ResponseValidation = new ResponseValidation {
    def description: List[Comment] = List(
      if (sameValues)
        "Verify that every point is reported in both objects, and that each point's latest event (g23) has the value " +
          "of its frozen counter (g21)"
      else "Verify that every point is reported in both objects")
    def validate(reporter: ProcedureReporter, responses: List[Message]): List[DeferredCheck] = {
      def values(points: Seq[GenIndexedPoint[MeasurementPoint]]): Map[Int, Long] =
        points.collect { case GenIndexedPoint(idx, c: CounterPoint) => idx -> c.value }.toMap
      val all = responses.headOption.map(_.copy(headers = responses.flatMap(_.headers)))
      val events = all.fold(Map.empty[Int, Long])(m => values(m.measurements(PointType.Event)))
      val frozen = all.fold(Map.empty[Int, Long])(m => values(m.measurements(PointType.Static)))
      if (events.keySet != frozen.keySet)
        throw new Exception(
          s"Points reported in only one object: events at ${(events.keySet -- frozen.keySet).toList.sorted
              .mkString(", ")}, frozen counters at ${(frozen.keySet -- events.keySet).toList.sorted.mkString(", ")}")
      if (sameValues) {
        val different = frozen.keys.filter(i => events(i) != frozen(i)).toList.sorted
        if (different.nonEmpty)
          throw new Exception(
            "The latest event and the frozen counter differ at " +
              different.map(i => s"$i (event ${events(i)}, frozen ${frozen(i)})").mkString(", "))
      }
      Nil
    }
  }

  /**
   * The read after a freeze: of g23 and g21 together where the DUT has frozen counter events, labeled as the events
   * branch, else of g21 alone, labeled as the other branch
   *
   * @param check what the step requires of the frozen counters (g21), as 2.7 does after its read
   */
  private def afterFreeze(
      options: OptionSource,
      seq: Sequence,
      eventsBranch: (String, String),
      frozenBranch: (String, String),
      check: ResponseValidation,
      sameValues: Boolean = true): List[LabeledStep] =
    if (withEvents(options))
      List(
        label(eventsBranch._1) --> send(Requests.readAllObjects(seq, Group23Var0, Group21Var0)),
        label(eventsBranch._2) --> expectAtLeastOneResponse(
          seq,
          eventsThenFrozenCounters && Validations.verifyConfirmRequestedIfRequired && bothObjects(sameValues) &&
            ofFrozenCounters(check))
      )
    else
      List(
        label(frozenBranch._1) --> send(Requests.readAllObjects(seq, Group21Var0)),
        label(frozenBranch._2) --> expectAtLeastOneResponse(seq, check)
      )

  // after a second freeze and clear with no counts between: zero, but for counters that can't be kept from counting,
  // which 3.1r1 exempts (8.16.2.2.4 step 13, 8.16.2.2.6 step 11)
  private[v31r1] def zeroExcept(counting: Set[Int]): MessageValidation = MessageValidation.check(
    if (counting.isEmpty) "Verify that the values returned are all zero"
    else
      "Verify that the values returned are all zero, but for the counters that can't be kept from counting " +
        s"(${counting.toList.sorted.mkString(", ")})") { message =>
    val nonZero = message.measurements(PointType.Static).collect {
      case GenIndexedPoint(idx, c: CounterPoint) if c.value != 0 && !counting.contains(idx) => idx
    }
    if (nonZero.nonEmpty) throw new Exception(s"Unexpected nonzero values at ${nonZero.mkString(", ")}")
  }

  // a step, e.g. "4.1", or steps, e.g. "4.2 to 8", as 3.1r1 numbers them
  private def label(steps: String): Label = Label(if (steps.contains(" to ")) s"steps: $steps" else s"step: $steps")

  private abstract class FreezeTest extends ProcedureDefinition {
    override protected def requiredOptions: List[OptionCondition] =
      List(Inputs.frozenCounter.points, OptionCondition(Inputs.frozenCounter.reads))
  }

  private def freezeTest(ack: Boolean): ProcedureDefinition = new FreezeTest {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      // 8.16.2.2.3's step 3 makes the read, which 8.16.2.2.5 numbers 3.1
      val firstEvents = if (ack) ("3", "3.1 to 3.3") else ("3.1", "3.2 to 3.5")
      List(
        1 --> send(getFreezeFunctionCode(ack), Sequence.one, AllObjectsHeader(Group20Var0)),
        2 --> expectedFreezeResponse(ack, Sequence.one)
      ) ++
        afterFreeze(options, Sequence.two, firstEvents, ("4.1", "4.2"), frozenCounters) ++
        List(
          5 --> PluginSteps.recordCurrentCounterValues(),
          6 --> PluginSteps.generateCounterInputData(),
          7 --> send(Requests.readAllObjects(Sequence.three, Group21Var0)),
          (8 to 11) --> expectAtLeastOneResponse(
            Sequence.three,
            frozenCounters && Validations.verifyFlagsComplyWithFlagNote &&
              PluginValidations.verifyRecordedCounterValues().acrossFragments
          ),
          12 --> send(getFreezeFunctionCode(ack), Sequence.four, AllObjectsHeader(Group20Var0)),
          13 --> expectedFreezeResponse(ack, Sequence.four)
        ) ++
        afterFreeze(
          options,
          Sequence.five,
          ("14.1", "14.2 to 16"),
          ("15.1", "15.2 to 16"),
          frozenCounters && PluginValidations.verifyAllCountersCurrentValues().acrossFragments
        )
    }
  }

  private def freezeAndClearTest(ack: Boolean): ProcedureDefinition = new FreezeTest {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      // without acknowledgement, the first read's checks end at step 7, and the second freeze has no step that
      // verifies the DUT doesn't respond
      val (firstEnd, second) = if (ack) (8, 11) else (7, 9)
      val nonZero = frozenCounters && Validations.verifyFlagsComplyWithFlagNote && verifyAllNonZero
      val zero = frozenCounters && zeroExcept(options.get(CounterOptions.alwaysCounting).map(_.value).toSet)
      List(
        1 --> PluginSteps.generateCounterInputData(),
        2 --> send(getFreezeAndClearFunctionCode(ack), Sequence.one, AllObjectsHeader(Group20Var0)),
        3 --> expectedFreezeResponse(ack, Sequence.one)
      ) ++
        afterFreeze(options, Sequence.two, ("4.1", s"4.2 to $firstEnd"), ("5.1", s"5.2 to $firstEnd"), nonZero) ++
        (if (ack)
           List(
             9 --> send(getFreezeAndClearFunctionCode(ack), Sequence.three, AllObjectsHeader(Group20Var0)),
             10 --> expectedFreezeResponse(ack, Sequence.three))
         else
           List(
             8 -->
               (send(getFreezeAndClearFunctionCode(ack), Sequence.three, AllObjectsHeader(Group20Var0)) +
                 expectedFreezeResponse(ack, Sequence.three)))) ++
        afterFreeze(
          options,
          Sequence.four,
          (s"$second.1", s"$second.2 to ${second + 2}"),
          (s"${second + 1}.1", s"${second + 1}.2 to ${second + 2}"),
          zero,
          // 8.16.2.2.4 step 11.5 asks only that every point be in both objects; 8.16.2.2.6 step 9.5 also that the
          // values match
          sameValues = !ack
        )
    }
  }

  val freeze: ProcedureDefinition = freezeTest(ack = true)
  val freezeAndClear: ProcedureDefinition = freezeAndClearTest(ack = true)
  val freezeNoAck: ProcedureDefinition = freezeTest(ack = false)
  val freezeAndClearNoAck: ProcedureDefinition = freezeAndClearTest(ack = false)
}
