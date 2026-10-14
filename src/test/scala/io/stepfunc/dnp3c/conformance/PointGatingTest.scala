// SPDX-License-Identifier: GPL-3.0-only
// Copyright Step Function I/O LLC

package io.stepfunc.dnp3c.conformance

import io.stepfunc.dnp3c.conformance.Placements.id
import io.stepfunc.dnp3c.conformance.api.{ Options, ProcedureDefinition }
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions
import io.stepfunc.dnp3c.conformance.api.options.SupportOptions.Inputs
import io.stepfunc.dnp3c.conformance.v27
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.matchers.should.Matchers
import org.scalatest.funsuite.AnyFunSuite

// a procedure about an input's events or disabling runs only for a DUT that has those points
@RunWith(classOf[JUnitRunner])
class PointGatingTest extends AnyFunSuite with Matchers {

  private def runs(
      procedure: ProcedureDefinition,
      input: SupportOptions.InputOptions,
      points: Boolean,
      feature: (SupportOptions.InputOptions => api.BooleanOption, Boolean)): Boolean =
    procedure
      .disabledBy(Options.from(Map(input.points.id -> points.toString, feature._1(input).id -> feature._2.toString)))
      .isEmpty

  // (input, procedures that test its events, procedures for a DUT without them)
  private val eventFamilies = List(
    (Inputs.binary, List(v27.Procedure8_14_2_2, v27.Procedure8_6_6_2_1), List(v27.Procedure8_14_2_1)),
    (Inputs.doubleBit, List(v27.Procedure8_22_2_2, v27.Procedure8_6_6_2_4), List(v27.Procedure8_22_2_1)),
    (Inputs.counter, List(v27.Procedure8_17_2_2, v27.Procedure8_6_6_2_3), List(v27.Procedure8_17_2_1)),
    (Inputs.analog, List(v27.Procedure8_19_2_2, v27.Procedure8_6_6_2_2), Nil)
  )

  test("event procedures need the points and their events; the procedure without them runs otherwise") {
    for ((input, withEvents, without) <- eventFamilies; points <- List(true, false); events <- List(true, false)) {
      val both = points && events
      withClue(s"${input.events.id} points=$points events=$events: ") {
        withEvents.foreach(p => runs(p, input, points, (_.events, events)) should equal(both))
        without.foreach(p => runs(p, input, points, (_.events, events)) should equal(!both))
      }
    }
  }

  test("8.15.2 needs binary inputs, their events and relative time") {
    val settings = for (points <- List(true, false); events <- List(true, false); relative <- List(true, false))
      yield (points, events, relative)
    for ((points, events, relative) <- settings) {
      val options = Options.from(Map(
        Inputs.binary.points.id -> points.toString,
        Inputs.binary.events.id -> events.toString,
        api.options.TimeSyncOptions.binaryInputChangeWithRelativeTime.id -> relative.toString
      ))
      withClue(s"points=$points events=$events relative=$relative: ")(
        v27.Procedure8_15_2.disabledBy(options).isEmpty should equal(points && events && relative))
    }
  }

  test("configurable device procedures need the points and disabling") {
    for ((input, procedure) <- List(
        Inputs.binary -> v27.Procedure8_13_2_3,
        Inputs.doubleBit -> v27.Procedure8_21_2_2,
        Inputs.counter -> v27.Procedure8_16_1_2_3);
      points <- List(true, false); disabling <- List(true, false))
      withClue(s"${procedure.id} points=$points disabling=$disabling: ")(
        runs(procedure, input, points, (_.disabling, disabling)) should equal(points && disabling))
  }
}
