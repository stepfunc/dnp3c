// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import scala.util.Try

import io.stepfunc.dnp3c.conformance.api.{ ConfigProblem, OptionSource, ProcedureDefinition, TestProcedure }
import io.stepfunc.dnp3c.conformance.api.options.{ OutstationOptions, TimeSyncOptions }
import io.stepfunc.dnp3c.conformance.api.options.{ AnalogInputOptions, AnalogOutputOptions, ClassOptions }
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.{ Inputs, Outputs }
import io.stepfunc.dnp3c.conformance.s8.helpers.{ InputType, Validations }
import io.stepfunc.dnp3c.dnp3.{ Comment, DeferredCheck, MessageValidation, ProcedureReporter }
import io.stepfunc.dnp3c.dnp3.app.{ Message, Variation }
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group51Var1, Group51Var2 }

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
 * of an input type alike), and from the clock's state where the procedure knows it. Where it doesn't, the response
 * must be consistent with one state throughout. The order of the events is checked against the order they were
 * generated, by the event checks the plugin does.
 */
object EventRules {

  /** how the device's clock stood when it recorded the events, as far as the procedure knows */
  sealed trait Clock
  object Clock {
    case object Synchronized extends Clock
    case object Unsynchronized extends Clock
    case object Unknown extends Clock
  }

  // a way to report the events: the variations they may use, and the CTOs that may come with them
  private case class Form(name: String, events: Variation, ctos: Set[Variation]) {
    def validation: MessageValidation = Validations.eventsWithCtos(List(events), ctos)
  }

  /**
   * What 3.1r1 needs configured for the procedures a run selects: the Device Profile's default variation for each input
   * whose event tests run; and at subset levels 2 and 3, reads of every input and of its events, which only a level 1
   * device may lack
   */
  def configurationProblems(options: OptionSource, selected: Iterable[TestProcedure]): List[ConfigProblem] = {
    val inputs = List(InputType.BinaryInput, InputType.DoubleBitBinaryInput)
    val level = options.get(OutstationOptions.subsetLevel)
    val allInputs = List(Inputs.binary, Inputs.doubleBit, Inputs.counter, Inputs.frozenCounter, Inputs.analog)
    val unreadable = allInputs.flatMap { input =>
      Option.when(level.exists(_ != 1) && !options.get(input.eventReads))(
        ConfigProblem(
          input.eventReads.id,
          "false",
          s"only a subset level 1 device may lack reads of ${input.longName} events; this one is configured for " +
            s"level ${level.getOrElse("")}"
        ))
    }
    val unreadableStatic = allInputs.flatMap { input =>
      Option.when(level.exists(_ != 1) && !options.get(input.reads))(
        ConfigProblem(
          input.reads.id,
          "false",
          s"only a subset level 1 device may lack reads of ${input.longName} points; this one is configured for level " +
            level.getOrElse("")
        ))
    }
    val tested = selected.map(_.definition).collect { case t: InputEvents.EventTest => t.input }.toSet
    val defaults = inputs.filter(tested.contains).flatMap { input =>
      Option.when(options.get(input.defaultEventVariation).isEmpty)(
        ConfigProblem(
          input.defaultEventVariation.id,
          "",
          s"required by 3.1r1's ${input.name} event tests, to check reads of ${input.anyVariation} against the Device " +
            "Profile's default"
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
    // 8.14.2-13 waits for the clock to go unsynchronized: how long is the manufacturer's to say
    val expiry = selected.map(_.definition).collect { case t: InputEvents.AllQualifierWithRelativeTime => t.input }
      .filter(input =>
        options.get(input.withRelativeTime) && options.get(TimeSyncOptions.supportSynchronizingTimeFromMaster))
      .take(1)
      .flatMap { input =>
        Option.when(options.get(TimeSyncOptions.timeToUnsynchronizedMs).isEmpty)(
          ConfigProblem(
            TimeSyncOptions.timeToUnsynchronizedMs.id,
            "",
            s"required by 3.1r1's ${input.name} relative time test, which waits for the clock to go unsynchronized"
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
    // 8.19.2.4 changes an analog input by its deadband, which only the device's configuration says, and must tell the
    // changed value from the first within the tolerance
    val valueReturn = selected.exists(p =>
      p.definition == AnalogValueReturn.valueReturn && p.definition.disabledBy(options).isEmpty)
    val deadband = Option.when(valueReturn && options.get(AnalogInputOptions.valueReturnDeadband).isEmpty)(
      ConfigProblem(
        AnalogInputOptions.valueReturnDeadband.id,
        "",
        "required by 3.1r1's 8.19.2.4, which changes the analog input by 150% of its deadband")) ++
      options.get(AnalogInputOptions.valueReturnDeadband).filter(d =>
        valueReturn && 2 * options.get(AnalogInputOptions.valueTolerance) >= AnalogValueReturn.change(d)).map(d =>
        ConfigProblem(
          AnalogInputOptions.valueTolerance.id,
          options.get(AnalogInputOptions.valueTolerance).toString,
          s"too wide for 3.1r1's 8.19.2.4 to tell the value it applies, ${AnalogValueReturn.change(d)} (150% of the " +
            "deadband) above the first, from the first"
        ))
    // 8.23: subset level 3 reads input points with start/stop ranges
    val rangeReads = Option.when(level.contains(3) && !options.get(SupportOptions.rangeReads))(
      ConfigProblem(
        SupportOptions.rangeReads.id,
        "false",
        "a subset level 3 device must read input points with start/stop ranges (8.23)"))
    // 8.26 assigns the classes of binary inputs the configuration names
    // a device without classes 1, 2 and 3 skips 8.26.2, noting it
    val allClasses = List(ClassOptions.supportClass1, ClassOptions.supportClass2, ClassOptions.supportClass3)
      .forall(options.get(_))
    def runs(definition: ProcedureDefinition): Boolean =
      allClasses && selected.exists(p => p.definition == definition && p.definition.disabledBy(options).isEmpty)
    val classPoints =
      (if (runs(AssignClass.assignClass))
         List(ClassOptions.class1BinaryInputs -> 1, ClassOptions.class2BinaryInputs -> 2)
       else Nil) ++
        (if (runs(AssignClass.noClass))
           List(
             ClassOptions.class1BinaryInputs -> 2,
             ClassOptions.class2BinaryInputs -> 2,
             ClassOptions.class3BinaryInputs -> 2)
         else Nil)
    val assignClass = classPoints.groupMapReduce(_._1)(_._2)(math.max).toList.flatMap((option, needed) =>
      Option.when(options.get(option).size < needed)(
        ConfigProblem(
          option.id,
          options.get(option).map(_.value).mkString(", "),
          s"3.1r1's 8.26 needs at least $needed")))
    unreadable ++ unreadableStatic ++ defaults ++ rejected ++ relativeRejected ++ expiry ++ frozenCounterEvents ++
      analogOutputs32Bit ++ deadband ++ rangeReads ++ assignClass
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
   * @param clock how the device's clock stood when it recorded the events, if the procedure knows
   */
  def consistent(
      input: InputType,
      requested: Variation,
      options: OptionSource,
      clock: => Clock = Clock.Unknown): MessageValidation = new MessageValidation {

    private def forms: List[Form] = {
      val preferredVariation = preferred(input, requested, options)
      val withTime = options.get(input.withTime) || options.get(input.withRelativeTime)
      val relativeTime = options.get(input.withRelativeTime)
      val noTime = Form("without time", input.noTimeVariation, Set.empty)
      val unsynchronized =
        Form("unsynchronized: relative time after g51v2", input.relativeTimeVariation, Set(Group51Var2))
      val synchronized =
        if (preferredVariation == input.relativeTimeVariation)
          Form("synchronized: relative time after g51v1", input.relativeTimeVariation, Set(Group51Var1))
        else Form(s"synchronized: $preferredVariation", preferredVariation, Set.empty)
      if (preferredVariation == input.noTimeVariation || !withTime) List(noTime)
      else
        clock match {
          case Clock.Synchronized => List(synchronized)
          case Clock.Unsynchronized => List(unsynchronized)
          // only a device that reports relative time can report unsynchronized events as the rules require
          case Clock.Unknown => if (relativeTime) List(synchronized, unsynchronized) else List(synchronized)
        }
    }

    def description: List[Comment] = List(Comment(
      s"Verify that the events are reported as 3.1r1's rules for event reporting require, for a read of $requested"))

    def validate(reporter: ProcedureReporter, message: Message): List[DeferredCheck] = {
      val allowed = forms
      if (!allowed.exists(f => Try(f.validation.validate(reporter, message)).isSuccess))
        throw new Exception(
          s"The events aren't reported as 3.1r1's rules require for a read of $requested; expected " +
            allowed.map(f => s"${f.name} (${f.events}${if (f.ctos.isEmpty) "" else s", ${f.ctos.mkString(" or ")}"})")
              .mkString(" or "))
      Nil
    }
  }
}
