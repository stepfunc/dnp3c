// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import io.stepfunc.dnp3c.conformance.api.{ ConfigProblem, OptionSource, ProcedureDefinition, TestProcedure }
import io.stepfunc.dnp3c.conformance.api.options.{ OutstationOptions, TimeSyncOptions }
import io.stepfunc.dnp3c.conformance.api.options.{ AnalogInputOptions, AnalogOutputOptions, ClassOptions }
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.{ Inputs, Outputs }
import io.stepfunc.dnp3c.conformance.common.{ EventTime, InputType }
import io.stepfunc.dnp3c.conformance.common.EventTime.{ Batches, Clock, Form }
import io.stepfunc.dnp3c.dnp3.{ Comment, DeferredCheck, MessageValidation, ProcedureReporter }
import io.stepfunc.dnp3c.dnp3.app.{ Message, Variation }

/**
 * 3.1r1's rules for reporting binary (8.14.1.1) and double-bit (8.22) input events: which variation reports each
 * queued event, by how it was recorded.
 *
 * The preferred variation is the one requested, or for variation 0 and class reads, the Device Profile's default. With
 * a preferred variation without time, every event is reported without time. With time: an event recorded with
 * synchronized time is reported in the preferred variation; one recorded with unsynchronized time, in the relative time
 * variation after an unsynchronized CTO (g51v2); one recorded without time, without time.
 *
 * How the events were recorded follows from the options, which say whether the device's events carry time (all points
 * of an input type alike), and from the clock's state where the procedure knows it, or else where the plugin reported
 * it while the events were generated (IntegrationPlugin.timeQuality). Where neither does, the response must be
 * consistent with one state throughout. The order of the events is checked against the order they were
 * generated, by the event checks the plugin does.
 */
object EventRules {

  /**
   * What 3.1r1 needs configured whatever procedures a run selects: e.g. at subset levels 2 and 3, reads of every input
   * and of its events, which only a level 1 device may lack. What a procedure needs is its own (configurationProblems).
   */
  def configurationProblems(options: OptionSource): List[ConfigProblem] = {
    val inputs = List(InputType.BinaryInput, InputType.DoubleBitBinaryInput)
    val level = options.get(OutstationOptions.subsetLevel)
    val unreadable = Inputs.all.flatMap { input =>
      Option.when(level.exists(_ != 1) && !options.get(input.eventReads))(
        ConfigProblem(
          input.eventReads.id,
          "false",
          s"only a subset level 1 device may lack reads of ${input.longName} events; this one is configured for " +
            s"level ${level.getOrElse("")}"
        ))
    }
    val unreadableStatic = Inputs.all.flatMap { input =>
      Option.when(level.exists(_ != 1) && !options.get(input.reads))(
        ConfigProblem(
          input.reads.id,
          "false",
          s"only a subset level 1 device may lack reads of ${input.longName} points; this one is configured for level " +
            level.getOrElse("")
        ))
    }
    val rejected = inputs.flatMap { input =>
      Option.when(options.get(input.withTimeRejected) && options.get(input.withTime))(
        ConfigProblem(
          input.withTimeRejected.id,
          "true",
          s"only a device without ${input.name} events with time answers a read of them with IIN2.1, but " +
            s"${input.withTime.id} is true"
        ))
    }
    val relativeRejected = inputs.flatMap { input =>
      Option.when(options.get(input.withRelativeTimeRejected) && options.get(input.withRelativeTime))(
        ConfigProblem(
          input.withRelativeTimeRejected.id,
          "true",
          s"only a device without ${input.name} events with relative time answers a read of them with IIN2.1, but " +
            s"${input.withRelativeTime.id} is true"
        ))
    }
    // 8.16.2: a subset level 3 device with frozen counters generates frozen counter events
    val frozenCounterEvents = Option.when(
      level.contains(3) && options.get(Inputs.frozenCounter.points) && !options.get(Inputs.frozenCounter.events))(
      ConfigProblem(
        Inputs.frozenCounter.events.id,
        "false",
        "a subset level 3 device with frozen counters must generate frozen counter events (8.16.2)"))
    // 8.4.1.2.13: a subset level 3 device with analog output commands accepts them with 32-bit values
    val analogOutputs32Bit = Option.when(
      level.contains(3) && options.get(Outputs.analog.commands) && !options.get(AnalogOutputOptions.commands32Bit))(
      ConfigProblem(
        AnalogOutputOptions.commands32Bit.id,
        "false",
        "a subset level 3 device with analog output commands must accept 32-bit ones (g41v1)"))
    // 8.23: subset level 3 reads input points with start/stop ranges
    val rangeReads = Option.when(level.contains(3) && !options.get(SupportOptions.rangeReads))(
      ConfigProblem(
        SupportOptions.rangeReads.id,
        "false",
        "a subset level 3 device must read input points with start/stop ranges (8.23)"))
    unreadable ++ unreadableStatic ++ rejected ++ relativeRejected ++ frozenCounterEvents ++ analogOutputs32Bit ++
      rangeReads
  }

  /** the preferred variation for a read of `requested`: itself, or for variation 0 the Device Profile's default */
  def preferred(input: InputType, requested: Variation, options: OptionSource): Variation =
    if (requested != input.anyVariation) requested
    else
      options.get(input.defaultEventVariation) match {
        case Some(n) => input.eventVariation(n)
        case None =>
          throw new Exception(
            s"${input.defaultEventVariation.id} isn't set: 3.1r1 checks a read of ${input.anyVariation} against the " +
              "Device Profile's default variation")
      }

  /**
   * The response reports the input's events as the rules require, for a read of `requested`
   *
   * @param clock how the device's clock stood when it recorded the events, if the procedure knows; if it doesn't, as
   *   the plugin reported it while generating them (EventTime.recorded), if it did
   * @param batches the batches of events the response reports
   */
  def consistent(
      input: InputType,
      requested: Variation,
      options: OptionSource,
      clock: => Clock = Clock.Unknown,
      batches: Batches = Batches.Latest): MessageValidation = new MessageValidation {

    private def forms(clock: Clock): List[Form] = {
      val preferredVariation = preferred(input, requested, options)
      val withTime = options.get(input.withTime) || options.get(input.withRelativeTime)
      if (preferredVariation == input.noTimeVariation || !withTime)
        List(Form("without time", List(input.noTimeVariation), Set.empty))
      else EventTime.withTime(input, preferredVariation, options.get(input.withRelativeTime), clock)
    }

    def description: List[Comment] = List(Comment(
      s"Verify that the events are reported as 3.1r1's rules for event reporting require, for a read of $requested, " +
        "by the state of the DUT's clock when it recorded them, where the procedure or the plugin knows it"))

    def validate(reporter: ProcedureReporter, message: Message): List[DeferredCheck] = {
      val known = clock match {
        case Clock.Unknown => EventTime.recorded(reporter, batches)
        case c => c
      }
      EventTime.oneOf(message, reporter, forms(known), s"3.1r1's rules require for a read of $requested", known)
      Nil
    }
  }
}
