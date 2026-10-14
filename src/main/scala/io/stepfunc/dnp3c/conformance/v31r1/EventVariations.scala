// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api._
import io.stepfunc.dnp3c.conformance.api.options.{ AnalogInputOptions, CounterOptions, OutstationOptions }
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.conformance.common.GenericSteps._
import io.stepfunc.dnp3c.conformance.common.Validations._
import io.stepfunc.dnp3c.dnp3._
import io.stepfunc.dnp3c.dnp3.app._
import io.stepfunc.dnp3c.dnp3.app.objects._

/**
 * 3.1r1's 8.25, Specific Variations for Events, for counters, frozen counters and analog inputs: two events, read in the
 * 32-bit variation and confirmed; two more, read in the 16-bit variation, which the DUT mustn't retransmit unconfirmed,
 * and which a second read gets again. Each event comes back in the variation requested, or in the other width for a
 * point the DUT keeps only in that one (counter.only16Bit and the like), with its value and its flags normal.
 *
 * A device without the type (the points; for frozen counters, their events), or a level 1 or 2 device, may answer the
 * first read with a Null Error Response with IIN2.1 or IIN2.2 (steps 3 and 4); and a device without the events with a
 * Null Response or IIN2.1, as 8.25.1 allows. The rest of the test is then skipped.
 */
object EventVariations {

  /**
   * @param supported whether the DUT supports the type, as step 3 asks: the points, or for frozen counters, their
   *   events
   * @param has whether the DUT has the events
   * @param generate generates two events, and generateSmall two that a 16-bit variation reports
   */
  final case class EventType(
      name: String,
      kind: PointKind,
      supported: List[OptionCondition],
      has: List[OptionCondition],
      wide: Variation,
      narrow: Variation,
      only16: IndexListOption,
      only32: IndexListOption,
      generate: TestStep,
      generateSmall: TestStep,
      values: MessageValidation)

  private val ONLINE = 0x01

  def test(t: EventType): ProcedureDefinition = new ProcedureDefinition {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] = {
      val has = Requirement.all(t.has*).unmet(options).isEmpty
      val supported = Requirement.all(t.supported*).unmet(options).isEmpty
      val levelThree = options.get(OutstationOptions.subsetLevel).contains(3)
      val only16 = options.get(t.only16).map(_.value).toSet
      val only32 = options.get(t.only32).map(_.value).toSet
      var first: List[(Int, Variation, String)] = Nil

      def events(message: Message): List[(Int, Variation, String)] =
        message.headers.flatMap(h =>
          h.iterate.map(item =>
            (item.idx match { case Indexed(i) => i; case NotIndexed => -1 }, h.variation, item.point.toString)))

      def check(requested: Variation): MessageValidation = MessageValidation.check(
        s"Verify that each event comes back in $requested, or in the other width for a point the DUT keeps only in " +
          "that one, with 1-octet (0x17) or 2-octet (0x28) indexes",
        "Verify that every event reports its flags normal (ONLINE set, all others clear)"
      ) { message =>
        if (message.headers.isEmpty) throw new Exception("Expected events in the response")
        message.headers.foreach { h =>
          if (h.qualifier != Qualifier.ONE_BYTE_COUNT_AND_INDEX && h.qualifier != Qualifier.TWO_BYTE_COUNT_AND_INDEX)
            throw new Exception(s"Unexpected qualifier ${h.qualifier} for ${h.variation}")
          h.iterate.foreach { item =>
            val index = item.idx match {
              case Indexed(i) => i
              case NotIndexed => throw new Exception(s"Unexpected ${h.variation} without indexes")
            }
            val expected =
              if (requested == t.wide && only16.contains(index)) t.narrow
              else if (requested == t.narrow && only32.contains(index)) t.wide
              else requested
            if (h.variation != expected)
              throw new Exception(s"Point $index came back as ${h.variation}, where $expected was expected")
            item.point match {
              case p: MeasurementPoint =>
                p.flags.filter(_ != ONLINE).foreach(f =>
                  throw new Exception(s"Point $index came back with flags 0x${"%02X".format(f & 0xFF)}"))
              case _ =>
            }
          }
        }
      }

      val response = t.values && verifyConfirmRequested

      // the first read: a device without the events, or at level 1 or 2, may answer with a Null Error Response, and
      // without them with a Null Response too
      val firstResponse = new TestStep {
        private val full = expectSingleResponse(Sequence.one, AutoConfirm.Yes, check(t.wide) && response)
        def description: List[Comment] =
          "If the DUT doesn't support the type, or isn't tested at subset level 3, and answers with a Null Error Response " +
            "with IIN2.1 or IIN2.2, it passes and the rest of the test is skipped; without the events, a Null " +
            "Response or IIN2.1 passes too" :: full.description
        def run(driver: TestDriver): List[DeferredCheck] =
          if (has && supported && levelThree) full.run(driver)
          else {
            val responses = driver.readAllResponses(Sequence.one, AutoConfirm.Yes)
            val answer = responses.headOption.filter(_ => responses.size == 1)
            def answers(v: MessageValidation): Boolean =
              answer.exists(m => scala.util.Try(v.validate(driver.reporter, m)).isSuccess)
            val nullError = ((!supported || !levelThree) && answers(isNullErrorResponse(IIN.objectErrors))) ||
              (!has && answers(isNullErrorResponse(IIN.OBJECT_UNKNOWN)))
            val plainNull = !has && answers(isNull)
            if (nullError || plainNull) {
              driver.reporter.note(
                s"the DUT answers a read of ${t.wide} with ${if (nullError) "IIN2.1 or IIN2.2" else "a Null Response"}" +
                  (if (has && supported) ", as a subset level 1 or 2 device may" else ", having no such events"))
              throw new EndProcedure("the DUT answered the first read as the test allows, without events")
            } else (check(t.wide) && response).toResponseValidation.validate(driver.reporter, responses)
          }
      }

      val recordFirst = MessageValidation.check("Record the events") { m => first = events(m) }
      val same = MessageValidation.check("Verify that the response reports the same events as the one before") { m =>
        if (events(m) != first) throw new Exception("The response doesn't report the same events as the one before")
      }

      List(
        1 -->
          (if (has) PluginSteps.setFlagsNormal(t.kind) + t.generate
           else describe(s"The DUT has no ${t.name}")),
        2 --> send(Requests.readAllObjects(Sequence.one, t.wide)),
        (3 to 6) --> firstResponse,
        7 --> t.generateSmall,
        8 --> send(Requests.readAllObjects(Sequence.two, t.narrow)),
        (9 to 10) --> expectSingleResponse(Sequence.two, AutoConfirm.No, check(t.narrow) && response && recordFirst),
        Label("step: 10.5") -->
          expectNoRetransmission(options.get(OutstationOptions.appConfirmTimeout)),
        Label("step: 10.6") --> send(Requests.readAllObjects(Sequence.three, t.narrow)),
        Label("steps: 10.7 to 10.8") -->
          expectSingleResponse(Sequence.three, AutoConfirm.Yes, check(t.narrow) && verifyConfirmRequested && same)
      )
    }
  }

  /** 8.25.2-1 and 8.25.2-2, which 3.1r1 gives no steps: earlier tests cover them */
  def coveredBy(what: String): ProcedureDefinition = new ProcedureDefinition {
    override protected def steps(options: OptionSource): Iterable[LabeledStep] =
      List(1 --> describe(s"3.1r1 gives this test no steps: $what covers it"))
  }

  val counters: EventType = EventType(
    "counter events",
    PointKind.Counter,
    List(OptionCondition(Inputs.counter.points)),
    List(OptionCondition(Inputs.counter.points), OptionCondition(Inputs.counter.events)),
    Group22Var1,
    Group22Var2,
    CounterOptions.only16Bit,
    CounterOptions.only32Bit,
    PluginSteps.generateExactCounterChangeEvents(2),
    PluginSteps.generateExactCounterChangeEvents(2),
    PluginValidations.verifyLatestCounterChangeEvents()
  )

  val frozenCounters: EventType = EventType(
    "frozen counter events",
    PointKind.FrozenCounter,
    List(OptionCondition(Inputs.frozenCounter.events)),
    List(OptionCondition(Inputs.frozenCounter.points), OptionCondition(Inputs.frozenCounter.events)),
    Group23Var1,
    Group23Var2,
    CounterOptions.frozenOnly16Bit,
    CounterOptions.frozenOnly32Bit,
    PluginSteps.generateExactFrozenCounterEvents(2),
    PluginSteps.generateExactFrozenCounterEvents(2),
    PluginValidations.verifyFrozenCounterEvents()
  )

  val analogInputs: EventType = EventType(
    "analog input events",
    PointKind.AnalogInput,
    List(OptionCondition(Inputs.analog.points)),
    List(OptionCondition(Inputs.analog.points), OptionCondition(Inputs.analog.events)),
    Group32Var1,
    Group32Var2,
    AnalogInputOptions.only16Bit,
    AnalogInputOptions.only32Bit,
    PluginSteps.generateExactAnalogInputChangeEvents(2),
    PluginSteps.generateExactSmallAnalogInputEvents(2),
    PluginValidations.verifyLatestAnalogInputChangeEvents()
  )
}
