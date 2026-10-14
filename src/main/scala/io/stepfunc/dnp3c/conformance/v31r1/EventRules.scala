// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.v31r1

import scala.util.Try

import io.stepfunc.dnp3c.conformance.api.{ ConfigProblem, OptionSource, TestProcedure }
import io.stepfunc.dnp3c.conformance.api.options.OutstationOptions
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
   * whose event tests run; and at subset levels 2 and 3, event reads, which only a level 1 device may lack
   */
  def configurationProblems(options: OptionSource, selected: Iterable[TestProcedure]): List[ConfigProblem] = {
    val inputs = List(InputType.BinaryInput, InputType.DoubleBitBinaryInput)
    val level = options.get(OutstationOptions.subsetLevel)
    val unreadable = inputs.flatMap { input =>
      Option.when(level.exists(_ != 1) && !options.get(input.support.eventReads))(
        ConfigProblem(
          input.support.eventReads.id,
          "false",
          s"only a subset level 1 device may lack reads of ${input.name} events; this one is configured for level " +
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
    unreadable ++ defaults ++ rejected
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
      clock: Clock = Clock.Unknown): MessageValidation = new MessageValidation {

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
