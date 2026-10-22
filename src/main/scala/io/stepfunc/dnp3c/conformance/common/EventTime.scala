// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance.common

import scala.util.Try

import io.stepfunc.dnp3c.dnp3.{ Comment, DeferredCheck, MessageValidation, ProcedureReporter, TimeQuality }
import io.stepfunc.dnp3c.dnp3.app.{ Message, Variation }
import io.stepfunc.dnp3c.dnp3.app.objects.{ Group51Var1, Group51Var2 }

/**
 * How binary and double-bit input events recorded with time are reported, by the state of the device's clock when it
 * recorded them: an event recorded while the clock was synchronized in the variation preferred; one recorded while it
 * was unsynchronized with relative time, after an unsynchronized CTO (g51v2). That's TB2017-003a 5.1.3.1.2, which
 * 3.1r1's 8.14.1.1 adopts, and which 2.7 applies only for a device that lists the bulletin.
 */
object EventTime {

  /** how the device's clock stood when it recorded the events, as far as the procedure, or the plugin, knows */
  enum Clock {
    case Synchronized, Unsynchronized
    // it changed while the events were recorded, so some may have been recorded each way
    case Changed
    case Unknown
  }

  /** which of the batches of events a procedure generated a response reports */
  enum Batches {
    // the latest batch only: the procedure confirmed the earlier ones
    case Latest
    // every batch since the procedure started
    case All
  }

  /**
   * How the clock stood while the batches were generated, by the time quality the plugin reported before and after each
   * (IntegrationPlugin.timeQuality): unknown if it didn't say, every time
   */
  def recorded(reporter: ProcedureReporter, batches: Batches): Clock = {
    val clocks = batches match {
      case Batches.Latest => reporter.eventClocks.lastOption.toList
      case Batches.All => reporter.eventClocks
    }
    val qualities = clocks.flatMap((before, after) => List(before, after))
    if (qualities.isEmpty || qualities.contains(None)) Clock.Unknown
    else
      qualities.flatten.distinct match {
        case List(TimeQuality.Synchronized) => Clock.Synchronized
        case List(TimeQuality.Unsynchronized) => Clock.Unsynchronized
        case _ => Clock.Changed
      }
  }

  // a way to report the events: the variations they may use, and the CTOs that may come with them
  final case class Form(name: String, events: List[Variation], ctos: Set[Variation]) {
    def validation: MessageValidation = Validations.eventsWithCtos(events, ctos)

    def describe: String =
      s"$name (${events.mkString(" or ")}${if (ctos.isEmpty) "" else s", ${ctos.mkString(" or ")}"})"
  }

  /**
   * The ways to report the input's events, recorded with time, for a read whose preferred variation has time
   *
   * @param relativeTime whether the device reports events with relative time: only one that does can report events
   *   recorded unsynchronized as the rule requires, so where the clock is unknown, one that doesn't reports them as
   *   synchronized
   */
  def withTime(input: InputType, preferred: Variation, relativeTime: Boolean, clock: Clock): List[Form] = {
    val relative = input.relativeTimeVariation
    val unsynchronized = Form("unsynchronized: relative time after g51v2", List(relative), Set(Group51Var2))
    val synchronized =
      if (preferred == relative) Form("synchronized: relative time after g51v1", List(relative), Set(Group51Var1))
      else Form(s"synchronized: $preferred", List(preferred), Set.empty)
    // each event as its own state requires, in any mix
    val either = Form(
      "synchronized and unsynchronized, each as its state requires",
      (synchronized.events ++ unsynchronized.events).distinct,
      synchronized.ctos ++ unsynchronized.ctos
    )
    clock match {
      case Clock.Synchronized => List(synchronized)
      case Clock.Unsynchronized => List(unsynchronized)
      case Clock.Changed => List(either)
      case Clock.Unknown => if (relativeTime) List(synchronized, unsynchronized) else List(synchronized)
    }
  }

  /**
   * For a device that implements TB2017-003a, a response to a read of `requested`, a variation with absolute time,
   * reports the events as 5.1.3.1.2 requires, by the clock's state as the plugin reported it while generating them. 2.7
   * checks this in place of the variation its steps require, and notes it on the result.
   *
   * @param relativeTime whether the device reports events with relative time (see withTime)
   */
  def tb2017003a(input: InputType, requested: Variation, relativeTime: Boolean): MessageValidation =
    new MessageValidation {
      private val bulletin = "TB2017-003a 5.1.3.1.2"

      def description: List[Comment] = List(Comment(
        s"Verify that the events are reported as $bulletin requires, which the DUT implements: $requested for events " +
          s"recorded while the clock was synchronized, ${input.relativeTimeVariation} after g51v2 for those recorded " +
          "while it was unsynchronized, by the clock's state as the plugin reports it"))

      def validate(reporter: ProcedureReporter, message: Message): List[DeferredCheck] = {
        // noted first, so the log of a check that fails says which rule it applied
        val note = s"The events were checked as $bulletin requires, which the DUT implements, in place of the steps' " +
          s"$requested only"
        if (!reporter.notes.contains(note)) reporter.note(note)
        val clock = recorded(reporter, Batches.Latest)
        oneOf(message, reporter, withTime(input, requested, relativeTime, clock), s"$bulletin requires", clock)
        Nil
      }
    }

  /** the response reports the events in one of the forms; what for names the rules, for the failure */
  def oneOf(message: Message, reporter: ProcedureReporter, forms: List[Form], what: String, clock: Clock): Unit =
    if (!forms.exists(f => Try(f.validation.validate(reporter, message)).isSuccess))
      throw new Exception(
        s"The events aren't reported as $what${describe(clock)}; expected ${forms.map(_.describe).mkString(" or ")}")

  private def describe(clock: Clock): String = clock match {
    case Clock.Synchronized => ", for events recorded while the clock was synchronized"
    case Clock.Unsynchronized => ", for events recorded while the clock was unsynchronized"
    case Clock.Changed => ", for events recorded while the clock went from one state to the other"
    case Clock.Unknown => ""
  }
}
